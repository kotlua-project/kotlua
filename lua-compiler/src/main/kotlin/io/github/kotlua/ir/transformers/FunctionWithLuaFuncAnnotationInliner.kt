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

import io.github.kotlua.ast.*
import io.github.kotlua.ir.utils.LuaGenerationContext
import io.github.kotlua.ir.utils.getLuaCode
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.ir.expressions.IrCall

class FunctionWithLuaFuncAnnotationInliner(
    private val luaFuncCall: IrCall,
    private val context: LuaGenerationContext,
) {
    private val function = getLuaFunctionImplementation()
    private val replacements = collectReplacementsForCall()

    fun generateResultStatements(): List<LuaStatement> =
        LuaNameRemappingTransformer(replacements)
            .remapBlock(function.block)
            .statements

    private fun getLuaFunctionImplementation(): LuaExpression.AnonymousFunction =
        luaFuncCall.symbol.owner.getLuaCode()
            ?: compilationException("Lua function not found", luaFuncCall)

    private fun collectReplacementsForCall(): Map<LuaName, LuaExpression> {
        val exprTransformer = IrElementToLuaExpressionTransformer()
        val translatedArguments =
            luaFuncCall.arguments.map {
                it?.accept(exprTransformer, context) ?: LuaExpression.NilLiteral
            }
        return function.parameters
            .zip(translatedArguments)
            .toMap()
    }
}

private class LuaNameRemappingTransformer(
    private val replacements: Map<LuaName, LuaExpression>,
) {
    fun remapBlock(block: LuaBlock): LuaBlock = block.copy(statements = block.statements.map(::remapStatement))

    fun remapCompositeBlock(block: LuaCompositeBlock): LuaCompositeBlock = block.copy(statements = block.statements.map(::remapStatement))

    private fun remapStatement(stmt: LuaStatement): LuaStatement =
        when (stmt) {
            LuaStatement.Empty,
            LuaStatement.Break,
            -> {
                stmt
            }

            is LuaBlock -> {
                remapBlock(stmt)
            }

            is LuaCompositeBlock -> {
                remapCompositeBlock(stmt)
            }

            is LuaStatement.Return -> {
                stmt.copy(
                    values = stmt.values.map(::remapExpression),
                )
            }

            is LuaStatement.LocalVarDeclaration -> {
                stmt.copy(
                    initializers = stmt.initializers.map(::remapExpression),
                )
            }

            is LuaStatement.LocalFunctionDeclaration -> {
                stmt.copy(
                    function = remapExpression(stmt.function) as LuaExpression.AnonymousFunction,
                )
            }

            is LuaStatement.FunctionDeclaration -> {
                stmt.copy(
                    target = remapLValue(stmt.target),
                    function = remapExpression(stmt.function) as LuaExpression.AnonymousFunction,
                )
            }

            is LuaStatement.Assignment -> {
                stmt.copy(
                    targets = stmt.targets.map(::remapLValue),
                    values = stmt.values.map(::remapExpression),
                )
            }

            is LuaStatement.If -> {
                stmt.copy(
                    condition = remapExpression(stmt.condition),
                    thenBlock = remapBlock(stmt.thenBlock),
                    elseIfs =
                        stmt.elseIfs.map { elseIf ->
                            elseIf.copy(
                                condition = remapExpression(elseIf.condition),
                                block = remapBlock(elseIf.block),
                            )
                        },
                    elseBlock = stmt.elseBlock?.let(::remapBlock),
                )
            }

            is LuaStatement.While -> {
                stmt.copy(
                    condition = remapExpression(stmt.condition),
                    body = remapBlock(stmt.body),
                )
            }

            is LuaStatement.RepeatUntil -> {
                stmt.copy(
                    body = remapBlock(stmt.body),
                    condition = remapExpression(stmt.condition),
                )
            }

            is LuaStatement.NumericFor -> {
                stmt.copy(
                    start = remapExpression(stmt.start),
                    limit = remapExpression(stmt.limit),
                    step = stmt.step?.let(::remapExpression),
                    body = remapBlock(stmt.body),
                )
            }

            is LuaStatement.GenericFor -> {
                stmt.copy(
                    iterators = stmt.iterators.map(::remapExpression),
                    body = remapBlock(stmt.body),
                )
            }

            is LuaExpression.FunctionCall -> {
                remapExpression(stmt) as LuaStatement
            }

            is LuaExpression.MethodCall -> {
                remapExpression(stmt) as LuaStatement
            }
        }

    private fun remapLValue(lvalue: LuaLValue): LuaLValue =
        when (lvalue) {
            is LuaName -> {
                (replacements[lvalue] as? LuaLValue) ?: lvalue
            }

            is LuaLValue.TableField -> {
                lvalue.copy(target = remapExpression(lvalue.target))
            }

            is LuaLValue.TableIndex -> {
                lvalue.copy(
                    target = remapExpression(lvalue.target),
                    index = remapExpression(lvalue.index),
                )
            }
        }

    private fun remapExpression(expr: LuaExpression): LuaExpression =
        when (expr) {
            is LuaName -> {
                replacements[expr] ?: expr
            }

            is LuaLValue.TableField -> {
                expr.copy(target = remapExpression(expr.target))
            }

            is LuaLValue.TableIndex -> {
                expr.copy(
                    target = remapExpression(expr.target),
                    index = remapExpression(expr.index),
                )
            }

            is LuaExpression.BinaryOp -> {
                expr.copy(
                    left = remapExpression(expr.left),
                    right = remapExpression(expr.right),
                )
            }

            is LuaExpression.UnaryOp -> {
                expr.copy(
                    operand = remapExpression(expr.operand),
                )
            }

            is LuaExpression.TableConstructor -> {
                expr.copy(
                    fields = expr.fields.map(::remapField),
                )
            }

            is LuaExpression.AnonymousFunction -> {
                expr.copy(
                    block = remapBlock(expr.block),
                )
            }

            is LuaExpression.FunctionCall -> {
                expr.copy(
                    target = remapExpression(expr.target),
                    args = expr.args.map(::remapExpression),
                )
            }

            is LuaExpression.MethodCall -> {
                expr.copy(
                    target = remapExpression(expr.target),
                    args = expr.args.map(::remapExpression),
                )
            }

            LuaExpression.NilLiteral,
            LuaExpression.VarargLiteral,
            is LuaExpression.BooleanLiteral,
            is LuaExpression.IntLiteral,
            is LuaExpression.LongLiteral,
            is LuaExpression.DoubleLiteral,
            is LuaExpression.StringLiteral,
            -> {
                expr
            }
        }

    private fun remapField(field: LuaField): LuaField =
        when (field) {
            is LuaField.Indexed -> {
                field.copy(
                    key = remapExpression(field.key),
                    value = remapExpression(field.value),
                )
            }

            is LuaField.Named -> {
                field.copy(
                    value = remapExpression(field.value),
                )
            }

            is LuaField.Positional -> {
                field.copy(
                    value = remapExpression(field.value),
                )
            }
        }
}
