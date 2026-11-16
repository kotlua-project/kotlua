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
package io.github.kotlua.ir.transformers

import io.github.kotlua.ast.LuaName
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.scope.LuaLocalScope
import io.github.kotlua.ast.scope.LuaRootScope
import io.github.kotlua.parser.LuaAstMapper
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.backend.js.lower.PropertyLazyInitLowering
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

fun translateLuaCodeIntoStatementList(code: IrExpression): List<LuaStatement>? {
    val luaCode = foldString(code) ?: return null

    val temporaryRootScope = LuaRootScope("<lua-code>")
    val temporaryScope = LuaLocalScope(temporaryRootScope, "lua")

    return LuaAstMapper(temporaryScope).mapCode(luaCode)
}

fun translateLuaCodeWithFreeNames(code: IrExpression): Pair<List<LuaStatement>, Map<String, LuaName>>? {
    val luaCode = foldString(code) ?: return null

    val temporaryRootScope = LuaRootScope("<lua-code>")
    val temporaryScope = LuaLocalScope(temporaryRootScope, "lua")

    val statements = LuaAstMapper(temporaryScope).mapCode(luaCode)
    return statements to temporaryRootScope.ownNames
}

// Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
// org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.foldString
private var IrField.lazyInitializerExpression: IrExpression? by irAttribute(copyByDefault = false)

private fun foldString(expression: IrExpression): String? {
    val builder = StringBuilder()
    var foldingFailed = false
    expression.acceptVoid(
        object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                foldingFailed = true
            }

            override fun visitGetValue(expression: IrGetValue) {
                expression.symbol.owner.acceptVoid(this)
            }

            override fun visitVariable(declaration: IrVariable) {
                declaration.initializer?.let {
                    it.acceptVoid(this)
                    return
                }

                super.visitVariable(declaration)
            }

            override fun visitGetField(expression: IrGetField) {
                val owner = expression.symbol.owner
                (owner.initializer?.expression ?: owner.lazyInitializerExpression)
                    ?.acceptVoid(this)
            }

            override fun visitCall(expression: IrCall) {
                val owner = expression.symbol.owner
                val propertySymbol = owner.correspondingPropertySymbol
                return when {
                    expression.origin == IrStatementOrigin.PLUS -> {
                        expression.acceptChildrenVoid(this)
                    }

                    expression.origin == PropertyLazyInitLowering.PROPERTY_INIT_FUN_CALL -> {
                        owner.body?.acceptChildrenVoid(InitFunVisitor())
                        expression.acceptChildrenVoid(this)
                    }

                    propertySymbol != null && owner == propertySymbol.owner.getter -> {
                        if (propertySymbol.owner.isConst) {
                            val initializer = propertySymbol.owner.backingField?.initializer
                            if (initializer != null) {
                                initializer.acceptChildrenVoid(this)
                            } else {
                                foldingFailed = true
                            }
                        } else {
                            owner.body?.acceptChildrenVoid(this)
                        }
                        expression.acceptChildrenVoid(this)
                    }

                    else -> {
                        super.visitCall(expression)
                    }
                }
            }

            override fun visitReturn(expression: IrReturn) {
                expression.acceptChildrenVoid(this)
            }

            override fun visitConst(expression: IrConst) {
                builder.append(expression.value)
            }

            override fun visitStringConcatenation(expression: IrStringConcatenation) = expression.acceptChildrenVoid(this)
        },
    )

    if (foldingFailed) return null

    return builder.toString()
}

private class InitFunVisitor : IrVisitorVoid() {
    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitSetField(expression: IrSetField) {
        expression.symbol.owner.lazyInitializerExpression = expression.value
    }
}
