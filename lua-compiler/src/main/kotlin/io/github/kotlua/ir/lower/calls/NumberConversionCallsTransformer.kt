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
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.util.OperatorNameConventions

class NumberConversionCallsTransformer(
    context: LuaIrBackendContext,
) : CallsTransformer {
    private val irBuiltIns = context.irBuiltIns
    private val symbols = context.symbols
    private val symbolToTransformer: SymbolToTransformer = hashMapOf()

    init {
        registerConversions(irBuiltIns.byteClass) { fn ->
            when (fn.name) {
                OperatorNameConventions.TO_BYTE,
                OperatorNameConventions.TO_SHORT,
                OperatorNameConventions.TO_INT,
                OperatorNameConventions.TO_FLOAT,
                OperatorNameConventions.TO_DOUBLE,
                -> symbolToTransformer.add(fn.symbol, ::useDispatchReceiver)

                OperatorNameConventions.TO_LONG -> symbolToTransformer.add(fn.symbol, symbols.luaLongFromInt)

                OperatorNameConventions.TO_CHAR -> symbolToTransformer.add(fn.symbol, symbols.luaIntToChar)
            }
        }

        registerConversions(irBuiltIns.shortClass) { fn ->
            when (fn.name) {
                OperatorNameConventions.TO_BYTE -> symbolToTransformer.add(fn.symbol, symbols.luaIntToByte)

                OperatorNameConventions.TO_SHORT,
                OperatorNameConventions.TO_INT,
                OperatorNameConventions.TO_FLOAT,
                OperatorNameConventions.TO_DOUBLE,
                -> symbolToTransformer.add(fn.symbol, ::useDispatchReceiver)

                OperatorNameConventions.TO_LONG -> symbolToTransformer.add(fn.symbol, symbols.luaLongFromInt)

                OperatorNameConventions.TO_CHAR -> symbolToTransformer.add(fn.symbol, symbols.luaIntToChar)
            }
        }

        registerConversions(irBuiltIns.intClass) { fn ->
            when (fn.name) {
                OperatorNameConventions.TO_BYTE -> symbolToTransformer.add(fn.symbol, symbols.luaIntToByte)

                OperatorNameConventions.TO_SHORT -> symbolToTransformer.add(fn.symbol, symbols.luaIntToShort)

                OperatorNameConventions.TO_INT,
                OperatorNameConventions.TO_FLOAT,
                OperatorNameConventions.TO_DOUBLE,
                -> symbolToTransformer.add(fn.symbol, ::useDispatchReceiver)

                OperatorNameConventions.TO_LONG -> symbolToTransformer.add(fn.symbol, symbols.luaLongFromInt)

                OperatorNameConventions.TO_CHAR -> symbolToTransformer.add(fn.symbol, symbols.luaIntToChar)
            }
        }

        registerConversions(irBuiltIns.longClass) { fn ->
            when (fn.name) {
                OperatorNameConventions.TO_BYTE -> symbolToTransformer.add(fn.symbol, symbols.luaLongToByte)

                OperatorNameConventions.TO_SHORT -> symbolToTransformer.add(fn.symbol, symbols.luaLongToShort)

                OperatorNameConventions.TO_INT -> symbolToTransformer.add(fn.symbol, symbols.luaLongToInt)

                OperatorNameConventions.TO_LONG -> symbolToTransformer.add(fn.symbol, ::useDispatchReceiver)

                OperatorNameConventions.TO_FLOAT,
                OperatorNameConventions.TO_DOUBLE,
                -> symbolToTransformer.add(fn.symbol, symbols.luaLongToNumber)

                OperatorNameConventions.TO_CHAR -> symbolToTransformer.add(fn.symbol, symbols.luaLongToChar)
            }
        }

        for (klass in listOf(irBuiltIns.floatClass, irBuiltIns.doubleClass)) {
            registerConversions(klass) { fn ->
                when (fn.name) {
                    OperatorNameConventions.TO_BYTE -> symbolToTransformer.add(fn.symbol, symbols.luaNumberToByte)

                    OperatorNameConventions.TO_SHORT -> symbolToTransformer.add(fn.symbol, symbols.luaNumberToShort)

                    OperatorNameConventions.TO_INT -> symbolToTransformer.add(fn.symbol, symbols.luaNumberToInt)

                    OperatorNameConventions.TO_LONG -> symbolToTransformer.add(fn.symbol, symbols.luaNumberToLong)

                    OperatorNameConventions.TO_FLOAT,
                    OperatorNameConventions.TO_DOUBLE,
                    -> symbolToTransformer.add(fn.symbol, ::useDispatchReceiver)

                    OperatorNameConventions.TO_CHAR -> symbolToTransformer.add(fn.symbol, symbols.luaNumberToChar)
                }
            }
        }

        registerConversions(irBuiltIns.numberClass) { fn ->
            when (fn.name) {
                OperatorNameConventions.TO_BYTE -> symbolToTransformer.add(fn.symbol, symbols.luaDynamicNumberToByte)

                OperatorNameConventions.TO_SHORT -> symbolToTransformer.add(fn.symbol, symbols.luaDynamicNumberToShort)

                OperatorNameConventions.TO_INT -> symbolToTransformer.add(fn.symbol, symbols.luaDynamicNumberToInt)

                OperatorNameConventions.TO_LONG -> symbolToTransformer.add(fn.symbol, symbols.luaDynamicNumberToLong)

                OperatorNameConventions.TO_FLOAT,
                OperatorNameConventions.TO_DOUBLE,
                -> symbolToTransformer.add(fn.symbol, symbols.luaDynamicNumberToDouble)

                OperatorNameConventions.TO_CHAR -> symbolToTransformer.add(fn.symbol, symbols.luaDynamicNumberToChar)
            }
        }

        registerConversions(irBuiltIns.charClass) { fn ->
            when (fn.name) {
                OperatorNameConventions.TO_BYTE -> symbolToTransformer.add(fn.symbol, symbols.luaCharToByte)

                OperatorNameConventions.TO_SHORT -> symbolToTransformer.add(fn.symbol, symbols.luaCharToShort)

                OperatorNameConventions.TO_INT,
                OperatorNameConventions.TO_FLOAT,
                OperatorNameConventions.TO_DOUBLE,
                -> symbolToTransformer.add(fn.symbol, symbols.luaCharToInt)

                OperatorNameConventions.TO_LONG -> symbolToTransformer.add(fn.symbol, symbols.luaCharToLong)

                OperatorNameConventions.TO_CHAR -> symbolToTransformer.add(fn.symbol, ::useDispatchReceiver)
            }
        }
    }

    private inline fun registerConversions(
        klass: IrClassSymbol,
        block: (org.jetbrains.kotlin.ir.declarations.IrSimpleFunction) -> Unit,
    ) {
        for (fn in klass.owner.functions) {
            if (fn.parameters.size == 1) {
                block(fn)
            }
        }
    }

    private fun useDispatchReceiver(call: IrFunctionAccessExpression): IrExpression =
        IrTypeOperatorCallImpl(
            startOffset = call.startOffset,
            endOffset = call.endOffset,
            type = call.type,
            operator = IrTypeOperator.REINTERPRET_CAST,
            typeOperand = call.type,
            argument = call.arguments[0]!!,
        )

    override fun transformFunctionAccess(
        call: IrFunctionAccessExpression,
        doNotIntrinsify: Boolean,
    ): IrExpression {
        if (doNotIntrinsify) return call
        return symbolToTransformer[call.symbol]?.invoke(call) ?: call
    }
}
