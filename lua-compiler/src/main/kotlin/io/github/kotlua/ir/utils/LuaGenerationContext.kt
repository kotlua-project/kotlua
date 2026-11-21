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
package io.github.kotlua.ir.utils

import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaLValue
import io.github.kotlua.ast.LuaName
import io.github.kotlua.ast.scope.LuaScope
import io.github.kotlua.name.LuaStandardClassIds
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.ir.util.parentAsClass

class LuaGenerationContext(
    val staticContext: LuaStaticContext,
    val currentScope: LuaScope,
    val localNames: NameTable<IrDeclaration> = NameTable(NameScope.EmptyScope),
    val currentThisName: LuaName? = null,
    val isConstructor: Boolean = false,
) {
    fun newLocalContext(
        function: IrFunction,
        scope: LuaScope,
    ): LuaGenerationContext {
        val childTable = NameTable<IrDeclaration>(parent = localNames)
        val generator = LuaLocalNameGenerator(childTable)
        function.parameters.forEach { it.accept(generator, null) }
        if (function is IrConstructor) {
            function.parentAsClass.thisReceiver?.accept(generator, null)
        }
        function.body?.accept(generator, null)

        val tempContext =
            LuaGenerationContext(
                staticContext = staticContext,
                currentScope = scope,
                localNames = childTable,
            )
        val thisName =
            when (function) {
                is IrConstructor -> function.parentAsClass.thisReceiver?.let { tempContext.getNameForValueDeclaration(it) }
                else -> function.dispatchReceiverParameter?.let { tempContext.getNameForValueDeclaration(it) }
            }

        return LuaGenerationContext(
            staticContext = staticContext,
            currentScope = scope,
            localNames = childTable,
            currentThisName = thisName,
            isConstructor = function is IrConstructor,
        )
    }

    fun getNameForValueDeclaration(declaration: IrDeclarationWithName): LuaName {
        val localName = localNames[declaration]
        if (localName != null) {
            return currentScope.findName(localName) ?: currentScope.declareName(localName)
        }
        return staticContext.irNamer.getNameForStaticDeclaration(declaration)
    }

    fun getNameForStaticDeclaration(declaration: IrDeclarationWithName): LuaName =
        staticContext.irNamer.getNameForStaticDeclaration(declaration)

    fun getNameForMemberFunction(function: IrSimpleFunction): LuaName = staticContext.irNamer.getNameForMemberFunction(function)

    fun getNameForMemberField(field: IrField): LuaName = staticContext.irNamer.getNameForMemberField(field)

    fun checkIfLuaCode(symbol: IrFunctionSymbol): Boolean = symbol == staticContext.backendContext.symbols.luaCode

    fun checkIfHasAssociatedLuaCode(symbol: IrFunctionSymbol): Boolean = symbol.owner.getLuaCode() != null

    fun getRefForStaticDeclaration(declaration: IrDeclarationWithName): LuaLValue {
        val name = getNameForStaticDeclaration(declaration)
        if (declaration.isEffectivelyExternal()) {
            staticContext.externalImportModuleVars[declaration]?.let { modVar ->
                return luaElementAccess(name, modVar)
            }
            if ('.' in name.identifier &&
                !declaration.hasAnnotation(LuaStandardClassIds.Annotations.LuaImport)
            ) {
                return name.identifier
                    .split('.')
                    .fold<String, LuaExpression?>(null) { acc, part -> luaElementAccess(part, acc) } as LuaLValue
            }
            return name
        }
        val isPrivate =
            when (declaration) {
                is IrField -> {
                    true
                }

                is IrDeclarationWithVisibility -> {
                    declaration.visibility == DescriptorVisibilities.PRIVATE ||
                        declaration.visibility == DescriptorVisibilities.PRIVATE_TO_THIS ||
                        declaration.visibility == DescriptorVisibilities.LOCAL
                }

                else -> {
                    false
                }
            }
        val container = if (isPrivate) staticContext.privateTableName else staticContext.moduleTableName
        return luaElementAccess(name, container)
    }
}
