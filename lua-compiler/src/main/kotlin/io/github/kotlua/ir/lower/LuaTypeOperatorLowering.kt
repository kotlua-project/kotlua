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
import io.github.kotlua.ir.lower.inline.RemoveInlineDeclarationsWithReifiedTypeParametersLowering
import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.phaser.PhasePrerequisites
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.types.IrDynamicType
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.isSubtypeOf
import org.jetbrains.kotlin.ir.visitors.IrTransformer

@PhasePrerequisites(
    RemoveInlineDeclarationsWithReifiedTypeParametersLowering::class,
    LuaCallableReferenceLowering::class,
)
class LuaTypeOperatorLowering(
    private val context: LuaIrBackendContext,
) : BodyLoweringPass {
    private val irBuiltIns = context.irBuiltIns
    private val symbols = context.symbols
    private val unitType = irBuiltIns.unitType
    private val unitValue
        get() = IrGetObjectValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unitType, irBuiltIns.unitClass)

    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        val rootParent = container as? IrDeclarationParent ?: container.parent
        irBody.transformChildren(
            object : IrTransformer<IrDeclarationParent>() {
                override fun visitDeclaration(
                    declaration: IrDeclarationBase,
                    data: IrDeclarationParent,
                ) = super.visitDeclaration(declaration, declaration as? IrDeclarationParent ?: data)

                override fun visitCall(
                    expression: IrCall,
                    data: IrDeclarationParent,
                ): IrElement {
                    val call = super.visitCall(expression, data)
                    if (call is IrCall && call.symbol == irBuiltIns.checkNotNullSymbol) {
                        val arg = call.arguments[0]!!
                        return buildEnsureNotNull(
                            value = arg,
                            resultType = call.type,
                            startOffset = call.startOffset,
                            endOffset = call.endOffset,
                            origin = call.origin,
                        )
                    }
                    return call
                }

                override fun visitTypeOperator(
                    expression: IrTypeOperatorCall,
                    data: IrDeclarationParent,
                ): IrExpression {
                    super.visitTypeOperator(expression, data)

                    return when (expression.operator) {
                        IrTypeOperator.IMPLICIT_CAST,
                        IrTypeOperator.IMPLICIT_DYNAMIC_CAST,
                        -> {
                            expression.wrapWithUnsafeCast(expression.argument)
                        }

                        IrTypeOperator.IMPLICIT_COERCION_TO_UNIT -> {
                            lowerCoercionToUnit(expression)
                        }

                        IrTypeOperator.IMPLICIT_NOTNULL -> {
                            buildEnsureNotNull(
                                value = expression.argument,
                                resultType = expression.typeOperand,
                                startOffset = expression.startOffset,
                                endOffset = expression.endOffset,
                            )
                        }

                        IrTypeOperator.CAST -> {
                            lowerCast(expression)
                        }

                        IrTypeOperator.IMPLICIT_INTEGER_COERCION,
                        IrTypeOperator.REINTERPRET_CAST,
                        IrTypeOperator.SAFE_CAST,
                        IrTypeOperator.INSTANCEOF,
                        IrTypeOperator.NOT_INSTANCEOF,
                        IrTypeOperator.SAM_CONVERSION,
                        -> {
                            expression
                        }
                    }
                }

                private fun IrTypeOperatorCall.wrapWithUnsafeCast(arg: IrExpression): IrExpression =
                    IrTypeOperatorCallImpl(
                        startOffset = startOffset,
                        endOffset = endOffset,
                        type = type,
                        operator = IrTypeOperator.REINTERPRET_CAST,
                        typeOperand = typeOperand,
                        argument = arg,
                    )

                private fun lowerCoercionToUnit(expression: IrTypeOperatorCall): IrExpression =
                    IrCompositeImpl(
                        startOffset = expression.startOffset,
                        endOffset = expression.endOffset,
                        type = unitType,
                        origin = null,
                        statements = listOf(expression.argument, unitValue),
                    )

                private fun lowerCast(expression: IrTypeOperatorCall): IrExpression {
                    val fromType = expression.argument.type
                    val toType = expression.typeOperand
                    val isNotNullAssertionRequired = fromType.isNullable() && !toType.isNullable()

                    val fromNotNullable = fromType.makeNotNull()
                    val toNotNullable = toType.makeNotNull()
                    val isUpcast =
                        fromNotNullable !is IrDynamicType &&
                            fromNotNullable.isSubtypeOf(toNotNullable, context.typeSystem)

                    if (isUpcast) {
                        val value =
                            if (isNotNullAssertionRequired) {
                                buildEnsureNotNull(
                                    value = expression.argument,
                                    resultType = toType,
                                    startOffset = expression.startOffset,
                                    endOffset = expression.endOffset,
                                )
                            } else {
                                expression.argument
                            }
                        return expression.wrapWithUnsafeCast(value)
                    }

                    // TODO: CCE checks once OOP is implemented
                    val value =
                        if (isNotNullAssertionRequired) {
                            buildEnsureNotNull(
                                value = expression.argument,
                                resultType = toType,
                                startOffset = expression.startOffset,
                                endOffset = expression.endOffset,
                            )
                        } else {
                            expression.argument
                        }
                    return expression.wrapWithUnsafeCast(value)
                }

                private fun buildEnsureNotNull(
                    value: IrExpression,
                    resultType: org.jetbrains.kotlin.ir.types.IrType,
                    startOffset: Int,
                    endOffset: Int,
                    origin: IrStatementOrigin? = null,
                ): IrExpression {
                    val notNullType = resultType.makeNotNull()
                    return IrCallImpl(
                        startOffset = startOffset,
                        endOffset = endOffset,
                        type = notNullType,
                        symbol = symbols.ensureNotNull,
                        typeArgumentsCount = 1,
                        origin = origin,
                    ).apply {
                        typeArguments[0] = notNullType
                        arguments[0] = value
                    }
                }
            },
            rootParent,
        )
    }
}
