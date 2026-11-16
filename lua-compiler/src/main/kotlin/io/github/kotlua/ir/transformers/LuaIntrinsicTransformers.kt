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
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaUnaryOperator
import io.github.kotlua.ir.LuaIrBackendContext
import io.github.kotlua.ir.utils.LuaGenerationContext
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol

private typealias LuaIrCallTransformer<T> = (T, context: LuaGenerationContext) -> LuaExpression
private typealias LuaIntrinsicMap = MutableMap<IrSymbol, LuaIrCallTransformer<*>>

class LuaIntrinsicTransformers(
    backendContext: LuaIrBackendContext,
) {
    private val transformers: Map<IrSymbol, LuaIrCallTransformer<*>>

    init {
        val symbols = backendContext.symbols

        transformers =
            hashMapOf<IrSymbol, LuaIrCallTransformer<*>>().apply {
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

                addIfNotNull(symbols.luaCode) { call, _ ->
                    compilationException("lua() should be handled by LuaCallTransformer", call)
                }
            }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun get(symbol: IrSimpleFunctionSymbol): LuaIrCallTransformer<IrCall>? = transformers[symbol] as LuaIrCallTransformer<IrCall>?

    @Suppress("UNCHECKED_CAST")
    operator fun get(symbol: IrConstructorSymbol): LuaIrCallTransformer<IrConstructorCall>? =
        transformers[symbol] as LuaIrCallTransformer<IrConstructorCall>?
}

private fun translateAllArguments(
    expression: IrFunctionAccessExpression,
    context: LuaGenerationContext,
): List<LuaExpression> {
    val exprTransformer = IrElementToLuaExpressionTransformer()
    return expression.symbol.owner.parameters.map { param ->
        expression.arguments[param.indexInParameters]
            ?.accept(exprTransformer, context)
            ?: LuaExpression.NilLiteral
    }
}

private fun LuaIntrinsicMap.addIfNotNull(
    symbol: IrSimpleFunctionSymbol?,
    t: LuaIrCallTransformer<IrCall>,
) {
    if (symbol != null) put(symbol, t)
}

private fun LuaIntrinsicMap.binOp(
    function: IrSimpleFunctionSymbol,
    op: LuaBinaryOperator,
) {
    withTranslatedArgs(function) { LuaExpression.BinaryOp(it[0], op, it[1]) }
}

private fun LuaIntrinsicMap.prefixOp(
    function: IrSimpleFunctionSymbol,
    op: LuaUnaryOperator,
) {
    withTranslatedArgs(function) { LuaExpression.UnaryOp(op, it[0]) }
}

private inline fun LuaIntrinsicMap.withTranslatedArgs(
    function: IrSimpleFunctionSymbol,
    crossinline t: (List<LuaExpression>) -> LuaExpression,
) {
    put(function) { call: IrCall, context -> t(translateAllArguments(call, context)) }
}
