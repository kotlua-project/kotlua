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

import io.github.kotlua.ast.LuaBlock
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaName
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.scope.LuaFunctionScope
import io.github.kotlua.ir.utils.LuaGenerationContext
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.ir.expressions.IrCall

class LuaCallTransformer(
    private val luaFuncCall: IrCall,
    private val context: LuaGenerationContext,
) {
    private val parsed = getStatements()

    fun generateStatement(): LuaStatement {
        if (parsed.isEmpty()) return LuaBlock(emptyList(), context.currentScope)

        val newStatements =
            parsed.toMutableList().apply {
                val lastReturn = last() as? LuaStatement.Return ?: return@apply
                val singleExpr = lastReturn.values.singleOrNull()
                when {
                    lastReturn.values.isEmpty() || singleExpr is LuaExpression.NilLiteral -> {
                        removeLastOrNull()
                    }

                    singleExpr is LuaExpression.FunctionCall || singleExpr is LuaExpression.MethodCall -> {
                        set(lastIndex, singleExpr as LuaStatement)
                    }

                    else -> {
                        set(
                            lastIndex,
                            LuaStatement.LocalVarDeclaration(
                                names = listOf(LuaName("_")),
                                initializers = lastReturn.values,
                            ),
                        )
                    }
                }
            }

        return when (newStatements.size) {
            0 -> LuaBlock(emptyList(), context.currentScope)

            1 if newStatements.single() !is LuaStatement.LocalVarDeclaration &&
                newStatements.single() !is LuaStatement.LocalFunctionDeclaration -> newStatements.single()

            else -> LuaBlock(newStatements, context.currentScope)
        }
    }

    fun generateExpression(): LuaExpression {
        if (parsed.isEmpty()) return LuaExpression.NilLiteral

        val lastStatement = parsed.last()
        val singleExpr =
            when (lastStatement) {
                is LuaStatement.Return -> lastStatement.values.singleOrNull()
                is LuaExpression.FunctionCall -> lastStatement
                is LuaExpression.MethodCall -> lastStatement
                else -> null
            }

        if (parsed.size == 1 && singleExpr != null) {
            return singleExpr
        }

        val newStatements = parsed.toMutableList()
        when (lastStatement) {
            is LuaStatement.Return -> {
                Unit
            }

            is LuaExpression.FunctionCall, is LuaExpression.MethodCall -> {
                newStatements[newStatements.lastIndex] = LuaStatement.Return(listOf(lastStatement as LuaExpression))
            }

            else -> {
                newStatements += LuaStatement.Return(listOf(LuaExpression.NilLiteral))
            }
        }

        val fnScope = LuaFunctionScope(context.currentScope, "<lua-inline>")
        val syntheticFunction =
            LuaExpression.AnonymousFunction(
                parameters = emptyList(),
                isVararg = false,
                block = LuaBlock(newStatements, fnScope),
                scope = fnScope,
            )
        return LuaExpression.FunctionCall(target = syntheticFunction, args = emptyList())
    }

    private fun getStatements(): List<LuaStatement> =
        when {
            context.checkIfLuaCode(luaFuncCall.symbol) -> {
                val arg =
                    luaFuncCall.arguments[0]
                        ?: compilationException("LuaCode is expected", luaFuncCall)
                translateLuaCodeIntoStatementList(arg)
                    ?: compilationException("Cannot compute lua code", luaFuncCall)
            }

            context.checkIfHasAssociatedLuaCode(luaFuncCall.symbol) -> {
                FunctionWithLuaFuncAnnotationInliner(luaFuncCall, context).generateResultStatements()
            }

            else -> {
                compilationException(
                    "`lua` function call or function with @LuaOutlinedFunction annotation expected",
                    luaFuncCall,
                )
            }
        }
}
