/*
 * Copyright 2026 The kotlua Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.kotlua.ir.dce

import io.github.kotlua.ir.LuaIrBackendContext
import io.github.kotlua.ir.lower.mainFunctionWrapper
import io.github.kotlua.ir.utils.LuaMainFunctionDetector
import io.github.kotlua.ir.utils.isComparableCompareTo
import io.github.kotlua.ir.utils.isIgnoredClass
import io.github.kotlua.ir.utils.isMethodOfAny
import io.github.kotlua.name.LuaStandardClassIds
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrFieldAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

fun runSimpleDce(
    modules: Iterable<IrModuleFragment>,
    entryModule: IrModuleFragment?,
    backendContext: LuaIrBackendContext,
) {
    val searchModules = entryModule?.let(::listOf) ?: modules

    val overriddenBy = hashMapOf<IrSimpleFunction, MutableList<IrSimpleFunction>>()

    fun indexClass(klass: IrClass) {
        for (decl in klass.declarations) {
            when (decl) {
                is IrSimpleFunction -> {
                    for (overridden in decl.overriddenSymbols) {
                        overriddenBy.getOrPut(overridden.owner) { mutableListOf() }.add(decl)
                    }
                }

                is IrClass -> {
                    indexClass(decl)
                }
            }
        }
    }

    for (module in modules) {
        for (file in module.files) {
            for (decl in file.declarations) {
                if (decl is IrClass) indexClass(decl)
            }
        }
    }

    val mainFunction =
        if (backendContext.callMain) {
            LuaMainFunctionDetector(backendContext).getMainFunctionOrNull(entryModule ?: modules.first())
        } else {
            null
        }
    val mainRoot = mainFunction?.mainFunctionWrapper ?: mainFunction

    val roots: List<IrDeclaration> =
        if (mainRoot != null) {
            listOf(mainRoot)
        } else {
            searchModules
                .flatMap { it.files }
                .flatMap { it.declarations }
                .filter { !it.isEffectivelyExternal() }
        }

    val reachable = mutableSetOf<IrDeclaration>()
    val queue = ArrayDeque<IrDeclaration>()

    fun enqueue(declaration: IrDeclaration?) {
        if (declaration != null && reachable.add(declaration)) {
            queue.addLast(declaration)
        }
    }

    roots.forEach(::enqueue)

    val scanner =
        object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitCall(expression: IrCall) {
                super.visitCall(expression)
                val callee = expression.symbol.owner
                enqueue(callee)
                if (callee.isEffectivelyExternal() && callee.parameters.any { it.isVararg }) {
                    enqueue(backendContext.symbols.luaUnpack.owner)
                }
                if (!callee.isLuaMultiReturnOrIntrinsic()) {
                    expression.type.classOrNull
                        ?.owner
                        ?.let { enqueue(it) }
                }
                for (typeArg in expression.typeArguments) {
                    typeArg?.classOrNull?.owner?.let(::enqueue)
                }
            }

            override fun visitGetEnumValue(expression: IrGetEnumValue) {
                super.visitGetEnumValue(expression)
                enqueue(expression.symbol.owner)
                enqueue(expression.symbol.owner.parentClassOrNull)
            }

            override fun visitConstructorCall(expression: IrConstructorCall) {
                super.visitConstructorCall(expression)
                enqueue(expression.symbol.owner)
                enqueue(expression.symbol.owner.constructedClass)
            }

            override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall) {
                super.visitDelegatingConstructorCall(expression)
                enqueue(expression.symbol.owner)
                enqueue(expression.symbol.owner.constructedClass)
            }

            override fun visitGetObjectValue(expression: IrGetObjectValue) {
                super.visitGetObjectValue(expression)
                enqueue(expression.symbol.owner)
            }

            override fun visitTypeOperator(expression: IrTypeOperatorCall) {
                super.visitTypeOperator(expression)
                expression.typeOperand.classOrNull
                    ?.owner
                    ?.let { enqueue(it) }
            }

            override fun visitVararg(expression: IrVararg) {
                super.visitVararg(expression)
                val arrayClass = expression.type.classOrNull?.owner ?: return
                enqueue(arrayClass)
                arrayClass.declarations.filterIsInstance<IrConstructor>().forEach(::enqueue)
            }

            override fun visitFieldAccess(expression: IrFieldAccessExpression) {
                super.visitFieldAccess(expression)
                enqueue(expression.symbol.owner)
            }
        }

    while (queue.isNotEmpty()) {
        when (val current = queue.removeFirst()) {
            is IrSimpleFunction -> {
                if (!current.isLuaMultiReturnOrIntrinsic()) {
                    current.returnType.classOrNull
                        ?.owner
                        ?.let { enqueue(it) }
                }
                current.parameters.forEach { param ->
                    param.type.classOrNull
                        ?.owner
                        ?.let { enqueue(it) }
                }
                overriddenBy[current]?.forEach { overrideFn ->
                    if (reachable.contains<IrDeclaration?>(overrideFn.parentClassOrNull)) {
                        enqueue(overrideFn)
                    }
                }
                current.body?.acceptVoid(scanner)
            }

            is IrFunction -> {
                if (!current.isLuaMultiReturnOrIntrinsic()) {
                    current.returnType.classOrNull
                        ?.owner
                        ?.let { enqueue(it) }
                }
                current.parameters.forEach { param ->
                    param.type.classOrNull
                        ?.owner
                        ?.let { enqueue(it) }
                }
                current.body?.acceptVoid(scanner)
            }

            is IrProperty -> {
                enqueue(current.getter)
                enqueue(current.setter)
                enqueue(current.backingField)
            }

            is IrField -> {
                current.type.classOrNull
                    ?.owner
                    ?.let { enqueue(it) }
                current.initializer?.acceptVoid(scanner)
            }

            is IrEnumEntry -> {
                enqueue(current.parentClassOrNull)
                current.initializerExpression?.acceptVoid(scanner)
                current.correspondingClass?.let { enqueue(it) }
            }

            is IrClass -> {
                if (!isIgnoredClass(current, backendContext)) {
                    enqueue(backendContext.symbols.luaDefineClass.owner)

                    current.superTypes.forEach { superType ->
                        superType.classOrNull?.owner?.let(::enqueue)
                    }

                    val isEnum = current.kind == ClassKind.ENUM_CLASS

                    for (decl in current.declarations) {
                        val shouldKeep =
                            when (decl) {
                                is IrConstructor -> {
                                    isEnum
                                }

                                is IrEnumEntry -> {
                                    isEnum
                                }

                                is IrSimpleFunction -> {
                                    decl.isMethodOfAny() ||
                                        decl.isComparableCompareTo() ||
                                        decl.overriddenSymbols.any { it.owner in reachable } ||
                                        (isEnum && (decl.name.asString() == "values" || decl.name.asString() == "valueOf"))
                                }

                                else -> {
                                    false
                                }
                            }

                        if (shouldKeep) {
                            enqueue(decl)
                        }
                    }
                }
            }
        }
    }

    for (module in modules) {
        for (file in module.files) {
            file.declarations.retainAll { declaration ->
                isKept(declaration, reachable)
            }
        }
    }
}

private fun isKept(
    declaration: IrDeclaration,
    reachable: Set<IrDeclaration>,
): Boolean {
    if (declaration in reachable) return true

    if (declaration is IrClass) {
        declaration.declarations.retainAll { isKept(it, reachable) }
        return declaration in reachable || declaration.declarations.isNotEmpty()
    }

    if (declaration is IrProperty) {
        val getterAlive = declaration.getter?.let { it in reachable } ?: false
        val setterAlive = declaration.setter?.let { it in reachable } ?: false
        val fieldAlive = declaration.backingField?.let { it in reachable } ?: false
        return getterAlive || setterAlive || fieldAlive
    }

    return false
}

private fun IrFunction.isLuaMultiReturnOrIntrinsic(): Boolean =
    hasAnnotation(LuaStandardClassIds.Annotations.LuaMultiReturn) ||
        hasAnnotation(LuaStandardClassIds.Annotations.LuaIntrinsic)
