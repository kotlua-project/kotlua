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
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.util.OperatorNameConventions

class PrimitiveContainerMemberCallTransformer(
    private val context: LuaIrBackendContext,
) : CallsTransformer {
    private val symbolToTransformer: SymbolToTransformer = hashMapOf()

    init {
        val irBuiltIns = context.irBuiltIns
        val symbols = context.symbols

        symbolToTransformer.run {
            irBuiltIns.stringClass.getPropertyGetter("length")?.let {
                add(it, symbols.luaLength)
            }
            for (fn in irBuiltIns.stringClass.owner.functions) {
                when (fn.name.asString()) {
                    "plus" if fn.parameters.size == 2 -> {
                        add(fn.symbol, symbols.luaConcat)
                    }

                    "get" if fn.parameters.size == 2 -> {
                        add(fn.symbol, symbols.luaStringGet)
                    }

                    "subSequence" if fn.parameters.size == 3 -> {
                        add(fn.symbol, symbols.luaStringSubSequence)
                    }
                }
            }

            for (fn in irBuiltIns.charClass.owner.functions) {
                when (fn.name) {
                    OperatorNameConventions.PLUS if fn.parameters.size == 2 && fn.parameters[1].type.isInt() -> {
                        add(fn.symbol, symbols.luaCharPlusInt)
                    }

                    OperatorNameConventions.MINUS if fn.parameters.size == 2 && fn.parameters[1].type.isChar() -> {
                        add(fn.symbol, symbols.luaCharMinusChar)
                    }

                    OperatorNameConventions.MINUS if fn.parameters.size == 2 && fn.parameters[1].type.isInt() -> {
                        add(fn.symbol, symbols.luaCharMinusInt)
                    }

                    OperatorNameConventions.INC if fn.parameters.size == 1 -> {
                        add(fn.symbol) { call -> makeCharStep(call, symbols.luaCharPlusInt) }
                    }

                    OperatorNameConventions.DEC if fn.parameters.size == 1 -> {
                        add(fn.symbol) { call -> makeCharStep(call, symbols.luaCharMinusInt) }
                    }
                }
            }
            symbols.charSequenceClass.getPropertyGetter("length")?.let {
                add(it, symbols.luaCharSequenceLength)
            }
            for (fn in symbols.charSequenceClass.owner.functions) {
                when (fn.name.asString()) {
                    "get" if fn.parameters.size == 2 -> {
                        add(fn.symbol, symbols.luaCharSequenceGet)
                    }

                    "subSequence" if fn.parameters.size == 3 -> {
                        add(fn.symbol, symbols.luaCharSequenceSubSequence)
                    }
                }
            }
        }
    }

    private fun makeCharStep(
        call: IrFunctionAccessExpression,
        opSymbol: IrSimpleFunctionSymbol,
    ): IrExpression =
        IrCallImpl(call.startOffset, call.endOffset, call.type, opSymbol, typeArgumentsCount = 0).apply {
            arguments[0] = call.arguments[0]
            arguments[1] = IrConstImpl.int(call.startOffset, call.endOffset, context.irBuiltIns.intType, 1)
        }

    override fun transformFunctionAccess(
        call: IrFunctionAccessExpression,
        doNotIntrinsify: Boolean,
    ): IrExpression {
        if (doNotIntrinsify) return call
        return symbolToTransformer[call.symbol]?.invoke(call) ?: call
    }
}
