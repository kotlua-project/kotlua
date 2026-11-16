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

class EqualityAndComparisonCallsTransformer(
    context: LuaIrBackendContext,
) : CallsTransformer {
    private val symbolToTransformer: SymbolToTransformer = hashMapOf()

    init {
        val irBuiltIns = context.irBuiltIns
        val symbols = context.symbols

        symbolToTransformer.run {
            add(irBuiltIns.eqeqSymbol, symbols.luaEq)
            add(irBuiltIns.eqeqeqSymbol, symbols.luaEq)
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
}
