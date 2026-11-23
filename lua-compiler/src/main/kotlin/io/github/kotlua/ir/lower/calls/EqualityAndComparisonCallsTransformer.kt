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
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrDynamicType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isBoolean
import org.jetbrains.kotlin.ir.types.isByte
import org.jetbrains.kotlin.ir.types.isChar
import org.jetbrains.kotlin.ir.types.isComparable
import org.jetbrains.kotlin.ir.types.isDouble
import org.jetbrains.kotlin.ir.types.isFloat
import org.jetbrains.kotlin.ir.types.isInt
import org.jetbrains.kotlin.ir.types.isLong
import org.jetbrains.kotlin.ir.types.isShort
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.hasShape
import org.jetbrains.kotlin.ir.util.isNullConst
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.util.OperatorNameConventions

class EqualityAndComparisonCallsTransformer(
    context: LuaIrBackendContext,
) : CallsTransformer {
    private val symbols = context.symbols
    private val irBuiltIns = context.irBuiltIns
    private val symbolToTransformer: SymbolToTransformer = hashMapOf()

    init {
        symbolToTransformer.run {
            add(irBuiltIns.eqeqeqSymbol, ::transformEqeqeqOperator)
            add(irBuiltIns.eqeqSymbol, ::transformEqeqOperator)
            for (fn in irBuiltIns.ieee754equalsFunByOperandType.values) {
                add(fn, symbols.luaEq)
            }

            add(irBuiltIns.booleanNotSymbol, symbols.luaNot)
            add(irBuiltIns.andandSymbol, symbols.luaAnd)
            add(irBuiltIns.ororSymbol, symbols.luaOr)

            for ((typeKey, fn) in irBuiltIns.lessFunByOperandType) {
                add(fn, if (typeKey == irBuiltIns.longClass) symbols.luaLongLt else symbols.luaLt)
            }
            for ((typeKey, fn) in irBuiltIns.lessOrEqualFunByOperandType) {
                add(fn, if (typeKey == irBuiltIns.longClass) symbols.luaLongLte else symbols.luaLte)
            }
            for ((typeKey, fn) in irBuiltIns.greaterFunByOperandType) {
                add(fn, if (typeKey == irBuiltIns.longClass) symbols.luaLongGt else symbols.luaGt)
            }
            for ((typeKey, fn) in irBuiltIns.greaterOrEqualFunByOperandType) {
                add(fn, if (typeKey == irBuiltIns.longClass) symbols.luaLongGte else symbols.luaGte)
            }
        }
    }

    override fun transformFunctionAccess(
        call: IrFunctionAccessExpression,
        doNotIntrinsify: Boolean,
    ): IrExpression {
        if (doNotIntrinsify) return call
        symbolToTransformer[call.symbol]?.let {
            return it(call)
        }
        return when (call.symbol.owner.name) {
            OperatorNameConventions.COMPARE_TO -> transformCompareToMethodCall(call)
            else -> call
        }
    }

    private fun transformCompareToMethodCall(call: IrFunctionAccessExpression): IrExpression {
        val function = call.symbol.owner as? IrSimpleFunction ?: return call
        if (function.parent !is IrClass) return call
        if (!function.hasShape(dispatchReceiver = true, regularParameters = 1)) return call

        val lhsType = function.parameters[0].type.makeNotNull()
        val rhsType = function.parameters[1].type.makeNotNull()

        if (lhsType.isLong() || rhsType.isLong()) {
            val lhsArg = call.arguments[0]!!
            val rhsArg = call.arguments[1]!!
            return when {
                lhsType.isLong() && rhsType.isLong() -> {
                    replaceCall(call, symbols.luaLongCompare)
                }

                lhsType.isLong() && (rhsType.isByte() || rhsType.isShort() || rhsType.isInt()) -> {
                    makeBinaryCall(call, symbols.luaLongCompare, lhsArg, wrapWithCall(rhsArg, symbols.luaLongFromInt))
                }

                (lhsType.isByte() || lhsType.isShort() || lhsType.isInt()) && rhsType.isLong() -> {
                    makeBinaryCall(call, symbols.luaLongCompare, wrapWithCall(lhsArg, symbols.luaLongFromInt), rhsArg)
                }

                lhsType.isLong() && (rhsType.isFloat() || rhsType.isDouble()) -> {
                    makeBinaryCall(call, symbols.luaCompareTo, wrapWithCall(lhsArg, symbols.luaLongToNumber), rhsArg)
                }

                (lhsType.isFloat() || lhsType.isDouble()) && rhsType.isLong() -> {
                    makeBinaryCall(call, symbols.luaCompareTo, lhsArg, wrapWithCall(rhsArg, symbols.luaLongToNumber))
                }

                else -> {
                    replaceCall(call, symbols.luaCompareTo)
                }
            }
        }

        fun IrSimpleFunction.isFakeOverriddenFromComparable(): Boolean =
            when {
                !isFakeOverride -> {
                    dispatchReceiverParameter != null &&
                        parentAsClass.thisReceiver?.type?.isComparable() == true
                }

                else -> {
                    overriddenSymbols.all { it.owner.isFakeOverriddenFromComparable() }
                }
            }

        return when {
            function.isMethodOfPrimitiveLuaType() || function.isFakeOverriddenFromComparable() -> {
                replaceCall(call, symbols.luaCompareTo)
            }

            else -> {
                call
            }
        }
    }

    private fun transformEqeqeqOperator(call: IrFunctionAccessExpression): IrExpression {
        val lhs = call.arguments[0]!!
        val rhs = call.arguments[1]!!

        return when {
            lhs.type.isLong() && rhs.type.isLong() -> replaceCall(call, symbols.luaLongEquals)
            else -> replaceCall(call, symbols.luaEq)
        }
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

            lhs.type.isLong() && rhs.type.isLong() -> {
                replaceCall(call, symbols.luaLongEquals)
            }

            else -> {
                replaceCall(call, symbols.luaEquals)
            }
        }
    }

    private fun IrFunction.isMethodOfPrimitiveLuaType(): Boolean =
        dispatchReceiverParameter?.let {
            val type = it.type.makeNotNull()
            type.isLuaPrimitive() || type.isLong()
        } ?: false

    private fun IrType.isLuaPrimitive(): Boolean =
        makeNotNull().run {
            isBoolean() || isByte() || isShort() || isInt() || isFloat() || isDouble() || isString() || isChar()
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
}
