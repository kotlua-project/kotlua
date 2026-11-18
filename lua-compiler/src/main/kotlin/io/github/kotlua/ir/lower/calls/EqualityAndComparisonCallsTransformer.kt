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
package io.github.kotlua.ir.lower.calls

import io.github.kotlua.ir.LuaIrBackendContext
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrDynamicType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.isNullConst

class EqualityAndComparisonCallsTransformer(
    context: LuaIrBackendContext,
) : CallsTransformer {
    private val symbols = context.symbols
    private val irBuiltIns = context.irBuiltIns
    private val symbolToTransformer: SymbolToTransformer = hashMapOf()

    init {
        symbolToTransformer.run {
            add(irBuiltIns.eqeqeqSymbol, symbols.luaEq)
            add(irBuiltIns.eqeqSymbol, ::transformEqeqOperator)
            for (fn in irBuiltIns.ieee754equalsFunByOperandType.values) {
                add(fn, symbols.luaEq)
            }

            add(irBuiltIns.booleanNotSymbol, symbols.luaNot)
            add(irBuiltIns.andandSymbol, symbols.luaAnd)
            add(irBuiltIns.ororSymbol, symbols.luaOr)

            for (fn in irBuiltIns.lessFunByOperandType.values) add(fn, symbols.luaLt)
            for (fn in irBuiltIns.lessOrEqualFunByOperandType.values) add(fn, symbols.luaLte)
            for (fn in irBuiltIns.greaterFunByOperandType.values) add(fn, symbols.luaGt)
            for (fn in irBuiltIns.greaterOrEqualFunByOperandType.values) add(fn, symbols.luaGte)
        }
    }

    override fun transformFunctionAccess(
        call: IrFunctionAccessExpression,
        doNotIntrinsify: Boolean,
    ): IrExpression {
        if (doNotIntrinsify) return call
        return symbolToTransformer[call.symbol]?.invoke(call) ?: call
    }

    private fun transformEqeqOperator(call: IrFunctionAccessExpression): IrExpression {
        val lhs = call.arguments[0]!!
        val rhs = call.arguments[1]!!

        return when {
            lhs.type is IrDynamicType || rhs.type is IrDynamicType -> {
                replaceCall(call, symbols.luaEq)
            }

            lhs.isNullConst() || rhs.isNullConst() -> {
                replaceCall(call, symbols.luaEq)
            }

            lhs.type.isLuaPrimitive() && rhs.type.isLuaPrimitive() -> {
                replaceCall(call, symbols.luaEq)
            }

            else -> {
                replaceCall(call, symbols.luaEquals)
            }
        }
    }

    private fun IrType.isLuaPrimitive(): Boolean =
        makeNotNull().run {
            isBoolean() || isByte() || isShort() || isInt() || isFloat() || isDouble() || isString() || isChar()
        }

    private fun replaceCall(
        call: IrFunctionAccessExpression,
        target: IrSimpleFunctionSymbol,
    ): IrExpression =
        IrCallImpl(
            startOffset = call.startOffset,
            endOffset = call.endOffset,
            type = call.type,
            symbol = target,
            typeArgumentsCount = 0,
            origin = call.origin,
        ).apply {
            for (i in call.arguments.indices) {
                arguments[i] = call.arguments[i]
            }
        }
}
