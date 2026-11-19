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

import io.github.kotlua.ast.LuaBinaryOperator
import io.github.kotlua.ast.LuaBlock
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaField
import io.github.kotlua.ast.LuaLValue
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.LuaUnaryOperator
import io.github.kotlua.ast.scope.LuaFunctionScope
import io.github.kotlua.ir.LuaIrBackendContext
import io.github.kotlua.ir.utils.LuaGenerationContext
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol

private typealias LuaIrCallTransformer<T, R> = (T, context: LuaGenerationContext) -> R
private typealias LuaIrExpressionCallTransformer<T> = LuaIrCallTransformer<T, LuaExpression>
private typealias LuaIrStatementCallTransformer<T> = LuaIrCallTransformer<T, LuaStatement>
private typealias LuaIntrinsicMap<T, R> = MutableMap<IrSymbol, LuaIrCallTransformer<T, R>>

abstract class LuaIntrinsicTransformers<in T, R> {
    protected abstract val transformers: Map<IrSymbol, LuaIrCallTransformer<T, R>>
}

class LuaExpressionIntrinsicTransformers(
    backendContext: LuaIrBackendContext,
) : LuaIntrinsicTransformers<IrFunctionAccessExpression, LuaExpression>() {
    override val transformers: Map<IrSymbol, LuaIrExpressionCallTransformer<IrFunctionAccessExpression>>

    init {
        val symbols = backendContext.symbols

        transformers =
            hashMapOf<IrSymbol, LuaIrExpressionCallTransformer<IrFunctionAccessExpression>>().apply {
                binOp(symbols.luaEq, LuaBinaryOperator.Eq)
                binOp(symbols.luaNeq, LuaBinaryOperator.Neq)
                binOp(symbols.luaGt, LuaBinaryOperator.Gt)
                binOp(symbols.luaGte, LuaBinaryOperator.Gte)
                binOp(symbols.luaLt, LuaBinaryOperator.Lt)
                binOp(symbols.luaLte, LuaBinaryOperator.Lte)

                prefixOp(symbols.luaNot, LuaUnaryOperator.Not)
                binOp(symbols.luaAnd, LuaBinaryOperator.And)
                binOp(symbols.luaOr, LuaBinaryOperator.Or)

                binOp(symbols.luaPlus, LuaBinaryOperator.Add)
                binOp(symbols.luaMinus, LuaBinaryOperator.Subtract)
                binOp(symbols.luaMult, LuaBinaryOperator.Multiply)
                binOp(symbols.luaDiv, LuaBinaryOperator.Divide)
                binOp(symbols.luaMod, LuaBinaryOperator.Modulo)
                binOp(symbols.luaPow, LuaBinaryOperator.Power)
                prefixOp(symbols.luaUnaryMinus, LuaUnaryOperator.Negate)

                binOp(symbols.luaConcat, LuaBinaryOperator.Concat)
                prefixOp(symbols.luaLength, LuaUnaryOperator.Length)

                put(symbols.luaEmptyTable) { _, _ ->
                    LuaExpression.TableConstructor(emptyList())
                }
                withTranslatedArgs(symbols.luaTableGet) { args ->
                    LuaLValue.TableIndex(target = args[0], index = args[1])
                }
                put(symbols.luaTableSet) { call, context ->
                    val args = translateAllArguments(call, context)
                    val fnScope = LuaFunctionScope(context.currentScope)
                    val assignment =
                        LuaStatement.Assignment(
                            targets = listOf(LuaLValue.TableIndex(target = args[0], index = args[1])),
                            values = listOf(args[2]),
                        )
                    val anonFn =
                        LuaExpression.AnonymousFunction(
                            parameters = emptyList(),
                            isVararg = false,
                            block = LuaBlock(listOf(assignment), fnScope),
                            scope = fnScope,
                        )
                    LuaExpression.FunctionCall(target = anonFn, args = emptyList())
                }

                addIfNotNull(symbols.luaCode) { call, _ ->
                    compilationException("lua() should be handled by LuaCallTransformer", call)
                }

                val sharedBox = symbols.genericSharedVariableBox

                put(sharedBox.constructor) { call, context ->
                    val args = translateAllArguments(call, context)
                    val initExpr = args.singleOrNull() ?: LuaExpression.NilLiteral
                    val fields =
                        if (initExpr is LuaExpression.NilLiteral) {
                            emptyList()
                        } else {
                            listOf(LuaField.Positional(initExpr))
                        }
                    LuaExpression.TableConstructor(fields)
                }

                withTranslatedArgs(sharedBox.load) { args ->
                    LuaLValue.TableIndex(target = args[0], index = LuaExpression.IntLiteral(1))
                }

                put(sharedBox.store) { call, context ->
                    val args = translateAllArguments(call, context)
                    val fnScope = LuaFunctionScope(context.currentScope)
                    val assignment =
                        LuaStatement.Assignment(
                            targets = listOf(LuaLValue.TableIndex(target = args[0], index = LuaExpression.IntLiteral(1))),
                            values = listOf(args[1]),
                        )
                    val anonFn =
                        LuaExpression.AnonymousFunction(
                            parameters = emptyList(),
                            isVararg = false,
                            block = LuaBlock(listOf(assignment), fnScope),
                            scope = fnScope,
                        )
                    LuaExpression.FunctionCall(target = anonFn, args = emptyList())
                }
            }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun get(symbol: IrSimpleFunctionSymbol): LuaIrCallTransformer<IrCall, LuaExpression>? =
        transformers[symbol] as LuaIrCallTransformer<IrCall, LuaExpression>?

    @Suppress("UNCHECKED_CAST")
    operator fun get(symbol: IrConstructorSymbol): LuaIrCallTransformer<IrConstructorCall, LuaExpression>? =
        transformers[symbol] as LuaIrCallTransformer<IrConstructorCall, LuaExpression>?
}

class LuaStatementIntrinsicTransformers(
    backendContext: LuaIrBackendContext,
) : LuaIntrinsicTransformers<IrFunctionAccessExpression, LuaStatement>() {
    override val transformers: Map<IrSymbol, LuaIrStatementCallTransformer<IrFunctionAccessExpression>>

    init {
        val symbols = backendContext.symbols

        transformers =
            hashMapOf<IrSymbol, LuaIrStatementCallTransformer<IrFunctionAccessExpression>>().apply {
                withTranslatedArgs(symbols.luaTableSet) { args ->
                    LuaStatement.Assignment(
                        targets = listOf(LuaLValue.TableIndex(target = args[0], index = args[1])),
                        values = listOf(args[2]),
                    )
                }
                put(symbols.unreachable) { _, _ ->
                    LuaStatement.Empty
                }
                withTranslatedArgs(symbols.genericSharedVariableBox.store) { args ->
                    LuaStatement.Assignment(
                        targets = listOf(LuaLValue.TableIndex(target = args[0], index = LuaExpression.IntLiteral(1))),
                        values = listOf(args[1]),
                    )
                }
            }
    }

    operator fun get(symbol: IrSimpleFunctionSymbol): LuaIrStatementCallTransformer<IrFunctionAccessExpression>? = transformers[symbol]
}

private fun <T : IrFunctionAccessExpression> translateAllArguments(
    expression: T,
    context: LuaGenerationContext,
): List<LuaExpression> {
    val exprTransformer = IrElementToLuaExpressionTransformer()
    return expression.symbol.owner.parameters.map { param ->
        expression.arguments[param.indexInParameters]
            ?.accept(exprTransformer, context)
            ?: LuaExpression.NilLiteral
    }
}

private fun <T, R> LuaIntrinsicMap<T, R>.addIfNotNull(
    symbol: IrSimpleFunctionSymbol?,
    t: LuaIrCallTransformer<T, R>,
) {
    if (symbol != null) put(symbol, t)
}

private fun <T : IrFunctionAccessExpression> LuaIntrinsicMap<T, LuaExpression>.binOp(
    function: IrSimpleFunctionSymbol,
    op: LuaBinaryOperator,
) {
    withTranslatedArgs(function) { LuaExpression.BinaryOp(it[0], op, it[1]) }
}

private fun <T : IrFunctionAccessExpression> LuaIntrinsicMap<T, LuaExpression>.prefixOp(
    function: IrSimpleFunctionSymbol,
    op: LuaUnaryOperator,
) {
    withTranslatedArgs(function) { LuaExpression.UnaryOp(op, it[0]) }
}

private inline fun <T : IrFunctionAccessExpression, R> LuaIntrinsicMap<T, R>.withTranslatedArgs(
    function: IrSimpleFunctionSymbol,
    crossinline t: (List<LuaExpression>) -> R,
) {
    put(function) { call: T, context -> t(translateAllArguments(call, context)) }
}
