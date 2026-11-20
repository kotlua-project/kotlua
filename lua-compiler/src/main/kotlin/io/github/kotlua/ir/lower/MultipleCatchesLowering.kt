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
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.isThrowable
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.name.Name

class MultipleCatchesLowering(
    private val context: LuaIrBackendContext,
) : BodyLoweringPass {
    private val litTrue: IrExpression
        get() = IrConstImpl.boolean(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.booleanType, true)

    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        val rootParent = container as? IrDeclarationParent ?: container.parent
        irBody.transform(
            object : IrTransformer<IrDeclarationParent>() {
                override fun visitDeclaration(
                    declaration: IrDeclarationBase,
                    data: IrDeclarationParent,
                ): IrStatement {
                    val parent = (declaration as? IrDeclarationParent) ?: data
                    return super.visitDeclaration(declaration, parent)
                }

                override fun visitTry(
                    aTry: IrTry,
                    data: IrDeclarationParent,
                ): IrExpression {
                    aTry.transformChildren(this, data)

                    if (aTry.catches.isEmpty()) return aTry
                    if (aTry.catches.size == 1 &&
                        aTry.catches[0]
                            .catchParameter.type
                            .isThrowable()
                    ) {
                        return aTry
                    }

                    val pendingException =
                        IrVariableImpl(
                            startOffset = UNDEFINED_OFFSET,
                            endOffset = UNDEFINED_OFFSET,
                            origin = IrDeclarationOrigin.CATCH_PARAMETER,
                            symbol = IrVariableSymbolImpl(),
                            name = Name.identifier($$"$p"),
                            type = context.irBuiltIns.throwableType,
                            isVar = false,
                            isConst = false,
                            isLateinit = false,
                        ).apply {
                            parent = data
                        }

                    fun getPendingException(): IrExpression =
                        IrGetValueImpl(
                            UNDEFINED_OFFSET,
                            UNDEFINED_OFFSET,
                            context.irBuiltIns.throwableType,
                            pendingException.symbol,
                        )

                    val branches = mutableListOf<IrBranch>()
                    var catchesAllThrowable = false

                    for (catch in aTry.catches) {
                        val catchParameter = catch.catchParameter
                        val catchType = catchParameter.type

                        catchParameter.parent = data
                        catchParameter.initializer =
                            if (catchType.isThrowable()) {
                                getPendingException()
                            } else {
                                buildImplicitCast(getPendingException(), catchType)
                            }
                        catch.result.patchDeclarationParents(data)

                        val useOffsetsFrom = catch.result as? IrBlock ?: catch
                        val catchBody =
                            IrBlockImpl(
                                startOffset = useOffsetsFrom.startOffset,
                                endOffset = useOffsetsFrom.endOffset,
                                type = catch.result.type,
                                origin = null,
                                statements = listOf(catchParameter, catch.result),
                            )

                        if (catchType.isThrowable()) {
                            branches += IrElseBranchImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, litTrue, catchBody)
                            catchesAllThrowable = true
                            break
                        } else {
                            branches +=
                                IrBranchImpl(
                                    UNDEFINED_OFFSET,
                                    UNDEFINED_OFFSET,
                                    buildIsCheck(getPendingException(), catchType),
                                    catchBody,
                                )
                        }
                    }

                    if (!catchesAllThrowable) {
                        val rethrow =
                            IrThrowImpl(
                                UNDEFINED_OFFSET,
                                UNDEFINED_OFFSET,
                                context.irBuiltIns.nothingType,
                                getPendingException(),
                            )
                        branches +=
                            IrElseBranchImpl(
                                UNDEFINED_OFFSET,
                                UNDEFINED_OFFSET,
                                litTrue,
                                IrBlockImpl(
                                    UNDEFINED_OFFSET,
                                    UNDEFINED_OFFSET,
                                    context.irBuiltIns.nothingType,
                                    null,
                                    listOf(rethrow),
                                ),
                            )
                    }

                    val whenExpr =
                        IrWhenImpl(
                            UNDEFINED_OFFSET,
                            UNDEFINED_OFFSET,
                            aTry.type,
                            null,
                            branches,
                        )

                    val newCatch =
                        IrCatchImpl(
                            UNDEFINED_OFFSET,
                            UNDEFINED_OFFSET,
                            pendingException,
                            whenExpr,
                        )

                    return IrTryImpl(
                        startOffset = aTry.startOffset,
                        endOffset = aTry.endOffset,
                        type = aTry.type,
                        tryResult = aTry.tryResult,
                        catches = listOf(newCatch),
                        finallyExpression = aTry.finallyExpression,
                    )
                }

                private fun buildIsCheck(
                    value: IrExpression,
                    toType: IrType,
                ): IrExpression =
                    IrTypeOperatorCallImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        context.irBuiltIns.booleanType,
                        IrTypeOperator.INSTANCEOF,
                        toType,
                        value,
                    )

                private fun buildImplicitCast(
                    value: IrExpression,
                    toType: IrType,
                ): IrExpression =
                    IrTypeOperatorCallImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        toType,
                        IrTypeOperator.IMPLICIT_CAST,
                        toType,
                        value,
                    )
            },
            rootParent,
        )
    }
}
