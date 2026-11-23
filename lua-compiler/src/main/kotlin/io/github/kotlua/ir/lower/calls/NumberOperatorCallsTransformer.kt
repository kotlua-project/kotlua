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
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.copyTypeArgumentsFrom
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.irError
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
                        symbolToTransformer.add(fn.symbol) { call ->
                            transformNumericBinary(call, isFloating, symbols.luaPlus, symbols.luaLongAdd)
                        }
                    }

                    OperatorNameConventions.MINUS if paramCount == 2 -> {
                        symbolToTransformer.add(fn.symbol) { call ->
                            transformNumericBinary(call, isFloating, symbols.luaMinus, symbols.luaLongSubtract)
                        }
                    }

                    OperatorNameConventions.TIMES if paramCount == 2 -> {
                        symbolToTransformer.add(fn.symbol) { call ->
                            transformNumericBinary(call, isFloating, symbols.luaMult, symbols.luaLongMultiply)
                        }
                    }

                    OperatorNameConventions.DIV if paramCount == 2 -> {
                        val rhsType = fn.parameters[1].type
                        val rhsIsIntegral = rhsType.isByte() || rhsType.isShort() || rhsType.isInt()
                        val defaultOp = if (!isFloating && rhsIsIntegral) symbols.luaIntDiv else symbols.luaDiv
                        symbolToTransformer.add(fn.symbol) { call ->
                            transformNumericBinary(call, isFloating, defaultOp, symbols.luaLongDivide)
                        }
                    }

                    OperatorNameConventions.REM if paramCount == 2 -> {
                        symbolToTransformer.add(fn.symbol) { call ->
                            transformNumericBinary(call, isFloating, symbols.luaRem, symbols.luaLongModulo)
                        }
                    }

                    OperatorNameConventions.UNARY_MINUS if paramCount == 1 -> {
                        if (isFloating) {
                            symbolToTransformer.add(fn.symbol, ::transformFloatingUnaryMinus)
                        } else {
                            add(fn.symbol, symbols.luaUnaryMinus)
                        }
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

                    OperatorNameConventions.RANGE_TO if paramCount == 2 -> {
                        symbolToTransformer.add(fn.symbol, ::transformRangeTo)
                    }

                    OperatorNameConventions.RANGE_UNTIL if paramCount == 2 -> {
                        symbolToTransformer.add(fn.symbol, ::transformRangeUntil)
                    }
                }
            }
        }

        for (fn in irBuiltIns.longClass.owner.functions) {
            val paramCount = fn.parameters.size
            when (fn.name) {
                OperatorNameConventions.PLUS if paramCount == 2 -> {
                    symbolToTransformer.add(fn.symbol) { call ->
                        transformLongBinary(call, symbols.luaPlus, symbols.luaLongAdd)
                    }
                }

                OperatorNameConventions.MINUS if paramCount == 2 -> {
                    symbolToTransformer.add(fn.symbol) { call ->
                        transformLongBinary(call, symbols.luaMinus, symbols.luaLongSubtract)
                    }
                }

                OperatorNameConventions.TIMES if paramCount == 2 -> {
                    symbolToTransformer.add(fn.symbol) { call ->
                        transformLongBinary(call, symbols.luaMult, symbols.luaLongMultiply)
                    }
                }

                OperatorNameConventions.DIV if paramCount == 2 -> {
                    symbolToTransformer.add(fn.symbol) { call ->
                        transformLongBinary(call, symbols.luaDiv, symbols.luaLongDivide)
                    }
                }

                OperatorNameConventions.REM if paramCount == 2 -> {
                    symbolToTransformer.add(fn.symbol) { call ->
                        transformLongBinary(call, symbols.luaRem, symbols.luaLongModulo)
                    }
                }

                OperatorNameConventions.UNARY_PLUS if paramCount == 1 -> {
                    symbolToTransformer.add(fn.symbol) { call -> call.arguments[0]!! }
                }

                OperatorNameConventions.UNARY_MINUS if paramCount == 1 -> {
                    add(fn.symbol, symbols.luaLongNegate)
                }

                OperatorNameConventions.INC if paramCount == 1 -> {
                    add(fn.symbol, symbols.luaLongInc)
                }

                OperatorNameConventions.DEC if paramCount == 1 -> {
                    add(fn.symbol, symbols.luaLongDec)
                }

                OperatorNameConventions.AND if paramCount == 2 -> {
                    add(fn.symbol, symbols.luaLongAnd)
                }

                OperatorNameConventions.OR if paramCount == 2 -> {
                    add(fn.symbol, symbols.luaLongOr)
                }

                OperatorNameConventions.XOR if paramCount == 2 -> {
                    add(fn.symbol, symbols.luaLongXor)
                }

                OperatorNameConventions.INV if paramCount == 1 -> {
                    add(fn.symbol, symbols.luaLongInv)
                }

                OperatorNameConventions.SHL if paramCount == 2 -> {
                    add(fn.symbol, symbols.luaLongShiftLeft)
                }

                OperatorNameConventions.SHR if paramCount == 2 -> {
                    add(fn.symbol, symbols.luaLongShiftRight)
                }

                OperatorNameConventions.USHR if paramCount == 2 -> {
                    add(fn.symbol, symbols.luaLongShiftRightUnsigned)
                }

                OperatorNameConventions.RANGE_TO if paramCount == 2 -> {
                    symbolToTransformer.add(fn.symbol, ::transformRangeTo)
                }

                OperatorNameConventions.RANGE_UNTIL if paramCount == 2 -> {
                    symbolToTransformer.add(fn.symbol, ::transformRangeUntil)
                }
            }
        }

        for (fn in irBuiltIns.charClass.owner.functions) {
            if (fn.parameters.size == 2) {
                when (fn.name) {
                    OperatorNameConventions.RANGE_TO -> add(fn.symbol, symbols.luaCharRangeToChar)
                    OperatorNameConventions.RANGE_UNTIL -> symbolToTransformer.add(fn.symbol, ::transformRangeUntil)
                }
            }
        }

        for (fn in irBuiltIns.booleanClass.owner.functions) {
            when (fn.name) {
                OperatorNameConventions.AND if fn.parameters.size == 2 -> add(fn.symbol, symbols.luaBoolAnd)
                OperatorNameConventions.OR if fn.parameters.size == 2 -> add(fn.symbol, symbols.luaBoolOr)
                OperatorNameConventions.XOR if fn.parameters.size == 2 -> add(fn.symbol, symbols.luaBoolXor)
                OperatorNameConventions.NOT if fn.parameters.size == 1 -> add(fn.symbol, symbols.luaNot)
            }
        }
    }

    /**
     * Preserves `-0.0` / `-0.0f` when negating a positive zero literal.
     * Lua 5.1 `addk` deduplicates constant `-0.0` with `+0.0` in the same function, so folding
     * `-(+0.0)` into a negative-zero `IrConst` lets codegen emit `-1.0 / (1.0 / 0.0)`.
     */
    private fun transformFloatingUnaryMinus(call: IrFunctionAccessExpression): IrExpression {
        val arg = call.arguments[0]!!
        if (arg is IrConst) {
            if (arg.kind == IrConstKind.Float && (arg.value as Float).toRawBits() == 0) {
                return IrConstImpl.float(call.startOffset, call.endOffset, call.type, -0.0f)
            }
            if (arg.kind == IrConstKind.Double && (arg.value as Double).toRawBits() == 0L) {
                return IrConstImpl.double(call.startOffset, call.endOffset, call.type, -0.0)
            }
        }
        return replaceCall(call, symbols.luaUnaryMinus)
    }

    private fun transformNumericBinary(
        call: IrFunctionAccessExpression,
        lhsIsFloating: Boolean,
        luaOp: IrSimpleFunctionSymbol,
        longOp: IrSimpleFunctionSymbol,
    ): IrExpression {
        val lhs = call.arguments[0]!!
        val rhs = call.arguments[1]!!
        if (!rhs.type.isLong()) {
            return replaceCall(call, luaOp)
        }
        return if (lhsIsFloating) {
            makeBinaryCall(call, luaOp, lhs, wrapWithCall(rhs, symbols.luaLongToNumber))
        } else {
            makeBinaryCall(call, longOp, wrapWithCall(lhs, symbols.luaLongFromInt), rhs)
        }
    }

    private fun transformLongBinary(
        call: IrFunctionAccessExpression,
        floatOp: IrSimpleFunctionSymbol,
        longOp: IrSimpleFunctionSymbol,
    ): IrExpression {
        val lhs = call.arguments[0]!!
        val rhs = call.arguments[1]!!
        val rhsType =
            call.symbol.owner.parameters[1]
                .type
        return when {
            rhsType.isFloat() || rhsType.isDouble() -> {
                makeBinaryCall(call, floatOp, wrapWithCall(lhs, symbols.luaLongToNumber), rhs)
            }

            rhsType.isByte() || rhsType.isShort() || rhsType.isInt() -> {
                makeBinaryCall(call, longOp, lhs, wrapWithCall(rhs, symbols.luaLongFromInt))
            }

            else -> {
                replaceCall(call, longOp)
            }
        }
    }

    private fun transformRangeTo(call: IrFunctionAccessExpression): IrExpression {
        if (call.arguments.size != 2) return call
        val lhsType =
            call.symbol.owner.parameters[0]
                .type
        val rhsType =
            call.symbol.owner.parameters[1]
                .type
        val lhs = call.arguments[0]!!
        val rhs = call.arguments[1]!!
        return when {
            lhsType.isLong() && rhsType.isLong() -> {
                replaceCall(call, symbols.luaLongRangeToLong)
            }

            lhsType.isLong() -> {
                makeBinaryCall(call, symbols.luaLongRangeToLong, lhs, wrapWithCall(rhs, symbols.luaLongFromInt))
            }

            rhsType.isLong() -> {
                makeBinaryCall(call, symbols.luaLongRangeToLong, wrapWithCall(lhs, symbols.luaLongFromInt), rhs)
            }

            rhsType.isByte() || rhsType.isShort() || rhsType.isInt() -> {
                replaceCall(call, symbols.luaNumberRangeToNumber)
            }

            else -> {
                call
            }
        }
    }

    private fun transformRangeUntil(call: IrFunctionAccessExpression): IrExpression {
        if (call.arguments.size != 2) return call
        val params = call.symbol.owner.parameters
        val function =
            symbols.rangeUntilFunctions[params[0].type to params[1].type]
                ?: irError("No 'until' function found for descriptor") {
                    withIrEntry("call.symbol.owner", call.symbol.owner)
                }
        return IrCallImpl(
            startOffset = call.startOffset,
            endOffset = call.endOffset,
            type = call.type,
            symbol = function,
            typeArgumentsCount = call.typeArguments.size,
            origin = call.origin,
        ).apply {
            copyTypeArgumentsFrom(call)
            for (i in call.arguments.indices) {
                arguments[i] = call.arguments[i]
            }
        }
    }

    private fun wrapWithCall(
        arg: IrExpression,
        target: IrSimpleFunctionSymbol,
    ): IrExpression =
        IrCallImpl(
            startOffset = arg.startOffset,
            endOffset = arg.endOffset,
            type = target.owner.returnType,
            symbol = target,
            typeArgumentsCount = 0,
        ).apply {
            arguments[0] = arg
        }

    private fun makeBinaryCall(
        call: IrFunctionAccessExpression,
        target: IrSimpleFunctionSymbol,
        lhs: IrExpression,
        rhs: IrExpression,
    ): IrExpression =
        IrCallImpl(
            startOffset = call.startOffset,
            endOffset = call.endOffset,
            type = call.type,
            symbol = target,
            typeArgumentsCount = 0,
            origin = call.origin,
        ).apply {
            arguments[0] = lhs
            arguments[1] = rhs
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
