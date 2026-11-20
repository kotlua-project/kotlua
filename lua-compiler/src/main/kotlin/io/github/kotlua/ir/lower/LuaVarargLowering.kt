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
package io.github.kotlua.ir.lower

import io.github.kotlua.ir.LuaIrBackendContext
import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.util.OperatorNameConventions

class LuaVarargLowering(
    private val context: LuaIrBackendContext,
) : BodyLoweringPass {
    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        val symbols = context.symbols
        val intType = context.irBuiltIns.intType
        val builder = context.createIrBuilder(container.symbol)

        irBody.transformChildrenVoid(
            object : IrElementTransformerVoid() {
                override fun visitCall(expression: IrCall): IrExpression {
                    if (expression.symbol == context.symbols.luaMultiReturn) {
                        val arg = expression.arguments[0]
                        if (arg is IrVararg) {
                            arg.elements.forEach { it.transformChildrenVoid(this) }
                            return expression
                        }
                    }
                    return super.visitCall(expression)
                }

                override fun visitVararg(expression: IrVararg): IrExpression {
                    expression.transformChildrenVoid(this)

                    if (expression.elements.none { it is IrSpreadElement }) {
                        return expression
                    }

                    val arrayClass = expression.type.classOrNull?.owner ?: return expression
                    val rawCtor = arrayClass.findRawArrayConstructor() ?: return expression

                    return builder.irBlock(expression.startOffset, expression.endOffset, resultType = expression.type) {
                        val tbl = irTemporary(irCall(symbols.luaEmptyTable), nameHint = "vararg_tbl")
                        val idx = irTemporary(irInt(1), nameHint = "vararg_idx", isMutable = true)

                        for (element in expression.elements) {
                            when (element) {
                                is IrSpreadElement -> {
                                    val spreadExpr = element.expression
                                    val spreadClass = spreadExpr.type.classOrNull?.owner ?: arrayClass
                                    val sizeGetter = spreadClass.findSizeGetter()
                                    val getOp = spreadClass.findGetOperator()
                                    val spreadVar = irTemporary(spreadExpr, nameHint = "spread")
                                    val spreadLen =
                                        irTemporary(
                                            irCall(sizeGetter).apply { dispatchReceiver = irGet(spreadVar) },
                                            nameHint = "spread_len",
                                        )
                                    val iVar = irTemporary(irInt(0), nameHint = "spread_i", isMutable = true)

                                    +irWhile().apply {
                                        condition =
                                            irCall(context.irBuiltIns.lessFunByOperandType.getValue(intType.classifierOrFail)).apply {
                                                arguments[0] = irGet(iVar)
                                                arguments[1] = irGet(spreadLen)
                                            }
                                        body =
                                            irBlock {
                                                val elemVal =
                                                    irCall(getOp).apply {
                                                        dispatchReceiver = irGet(spreadVar)
                                                        arguments[1] = irGet(iVar)
                                                    }
                                                +irCall(symbols.luaTableSet).apply {
                                                    arguments[0] = irGet(tbl)
                                                    arguments[1] = irGet(idx)
                                                    arguments[2] = elemVal
                                                }
                                                +irSet(
                                                    idx,
                                                    irCall(context.irBuiltIns.intPlusSymbol).apply {
                                                        arguments[0] = irGet(idx)
                                                        arguments[1] = irInt(1)
                                                    },
                                                )
                                                +irSet(
                                                    iVar,
                                                    irCall(context.irBuiltIns.intPlusSymbol).apply {
                                                        arguments[0] = irGet(iVar)
                                                        arguments[1] = irInt(1)
                                                    },
                                                )
                                            }
                                    }
                                }

                                is IrExpression -> {
                                    +irCall(symbols.luaTableSet).apply {
                                        arguments[0] = irGet(tbl)
                                        arguments[1] = irGet(idx)
                                        arguments[2] = element
                                    }
                                    +irSet(
                                        idx,
                                        irCall(context.irBuiltIns.intPlusSymbol).apply {
                                            arguments[0] = irGet(idx)
                                            arguments[1] = irInt(1)
                                        },
                                    )
                                }
                            }
                        }

                        val totalSize =
                            irCall(context.irBuiltIns.intPlusSymbol).apply {
                                arguments[0] = irGet(idx)
                                arguments[1] = irInt(-1)
                            }
                        +IrConstructorCallImpl
                            .fromSymbolOwner(
                                startOffset = UNDEFINED_OFFSET,
                                endOffset = UNDEFINED_OFFSET,
                                type = expression.type,
                                constructorSymbol = rawCtor.symbol,
                            ).apply {
                                arguments[0] = irGet(tbl)
                                arguments[1] = totalSize
                                arguments[2] = irTrue()
                            }
                    }
                }
            },
        )
    }
}

internal fun IrClass.findRawArrayConstructor(): IrConstructor? =
    declarations
        .filterIsInstance<IrConstructor>()
        .firstOrNull { it.parameters.size == 3 }

private fun IrClass.findSizeGetter(): IrSimpleFunction =
    declarations.filterIsInstance<IrSimpleFunction>().first {
        it.name.asString() == "<get-size>" && it.parameters.size == 1
    }

private fun IrClass.findGetOperator(): IrSimpleFunction =
    declarations.filterIsInstance<IrSimpleFunction>().first {
        it.name == OperatorNameConventions.GET && it.parameters.size == 2
    }
