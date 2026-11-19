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
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.util.OperatorNameConventions

class NumberOperatorCallsTransformer(
    private val context: LuaIrBackendContext,
) : CallsTransformer {
    private val symbols = context.symbols
    private val irBuiltIns = context.irBuiltIns
    private val symbolToTransformer: SymbolToTransformer = hashMapOf()

    init {
        val integralClasses =
            listOf(
                irBuiltIns.byteClass,
                irBuiltIns.shortClass,
                irBuiltIns.intClass,
            )
        val floatingClasses =
            listOf(
                irBuiltIns.floatClass,
                irBuiltIns.doubleClass,
            )

        for (klass in integralClasses + floatingClasses) {
            val isFloating = klass in floatingClasses
            for (fn in klass.owner.functions) {
                val paramCount = fn.parameters.size
                when (fn.name) {
                    OperatorNameConventions.PLUS if paramCount == 2 -> {
                        add(fn.symbol, symbols.luaPlus)
                    }

                    OperatorNameConventions.MINUS if paramCount == 2 -> {
                        add(fn.symbol, symbols.luaMinus)
                    }

                    OperatorNameConventions.TIMES if paramCount == 2 -> {
                        add(fn.symbol, symbols.luaMult)
                    }

                    OperatorNameConventions.DIV if paramCount == 2 -> {
                        val rhsType = fn.parameters[1].type
                        val rhsIsIntegral = rhsType.isByte() || rhsType.isShort() || rhsType.isInt()
                        if (!isFloating && rhsIsIntegral) {
                            add(fn.symbol, symbols.luaIntDiv)
                        } else {
                            add(fn.symbol, symbols.luaDiv)
                        }
                    }

                    OperatorNameConventions.REM if paramCount == 2 -> {
                        add(fn.symbol, symbols.luaRem)
                    }

                    OperatorNameConventions.UNARY_MINUS if paramCount == 1 -> {
                        add(fn.symbol, symbols.luaUnaryMinus)
                    }

                    OperatorNameConventions.UNARY_PLUS if paramCount == 1 -> {
                        symbolToTransformer.add(fn.symbol) { call -> call.arguments[0]!! }
                    }

                    OperatorNameConventions.INC if paramCount == 1 -> {
                        symbolToTransformer.add(fn.symbol) { call ->
                            makeBinaryIntrinsic(call, symbols.luaPlus, isFloating)
                        }
                    }

                    OperatorNameConventions.DEC if paramCount == 1 -> {
                        symbolToTransformer.add(fn.symbol) { call ->
                            makeBinaryIntrinsic(call, symbols.luaMinus, isFloating)
                        }
                    }

                    OperatorNameConventions.AND if klass == irBuiltIns.intClass && paramCount == 2 -> {
                        add(fn.symbol, symbols.luaBitAnd)
                    }

                    OperatorNameConventions.OR if klass == irBuiltIns.intClass && paramCount == 2 -> {
                        add(fn.symbol, symbols.luaBitOr)
                    }

                    OperatorNameConventions.XOR if klass == irBuiltIns.intClass && paramCount == 2 -> {
                        add(fn.symbol, symbols.luaBitXor)
                    }

                    OperatorNameConventions.SHL if klass == irBuiltIns.intClass && paramCount == 2 -> {
                        add(fn.symbol, symbols.luaBitShl)
                    }

                    OperatorNameConventions.SHR if klass == irBuiltIns.intClass && paramCount == 2 -> {
                        add(fn.symbol, symbols.luaBitShr)
                    }

                    OperatorNameConventions.USHR if klass == irBuiltIns.intClass && paramCount == 2 -> {
                        add(fn.symbol, symbols.luaBitUshr)
                    }

                    OperatorNameConventions.INV if klass == irBuiltIns.intClass && paramCount == 1 -> {
                        add(fn.symbol, symbols.luaBitNot)
                    }
                }
            }
        }
    }

    private fun makeBinaryIntrinsic(
        call: IrFunctionAccessExpression,
        opSymbol: IrSimpleFunctionSymbol,
        isFloating: Boolean,
    ): IrExpression {
        val one =
            if (isFloating) {
                IrConstImpl.double(call.startOffset, call.endOffset, irBuiltIns.doubleType, 1.0)
            } else {
                IrConstImpl.int(call.startOffset, call.endOffset, irBuiltIns.intType, 1)
            }
        return IrCallImpl(call.startOffset, call.endOffset, call.type, opSymbol, typeArgumentsCount = 0).apply {
            arguments[0] = call.arguments[0]
            arguments[1] = one
        }
    }

    private fun add(
        from: IrSimpleFunctionSymbol,
        to: IrSimpleFunctionSymbol,
    ) = symbolToTransformer.add(from, to)

    override fun transformFunctionAccess(
        call: IrFunctionAccessExpression,
        doNotIntrinsify: Boolean,
    ): IrExpression {
        if (doNotIntrinsify) return call
        return symbolToTransformer[call.symbol]?.invoke(call) ?: call
    }
}
