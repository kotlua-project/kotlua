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
import org.jetbrains.kotlin.backend.common.lower.irBlockBody
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.backend.common.phaser.PhasePrerequisites
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrReturnTargetSymbol
import org.jetbrains.kotlin.ir.symbols.IrReturnableBlockSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.Name

@PhasePrerequisites(MultipleCatchesLowering::class)
class LuaTryCatchLowering(
    private val context: LuaIrBackendContext,
) : BodyLoweringPass {
    private val symbols = context.symbols
    private val irBuiltIns = context.irBuiltIns

    private sealed interface JumpTarget {
        data class Return(
            val symbol: IrReturnTargetSymbol,
        ) : JumpTarget

        data class Break(
            val loop: IrLoop,
        ) : JumpTarget

        data class Continue(
            val loop: IrLoop,
        ) : JumpTarget
    }

    val tripleClass =
        symbols.luaPCall.owner.returnType.classOrNull!!
            .owner
    val comp1 = tripleClass.declarations.filterIsInstance<IrSimpleFunction>().first { it.name.asString() == "component1" }
    val comp2 = tripleClass.declarations.filterIsInstance<IrSimpleFunction>().first { it.name.asString() == "component2" }
    val comp3 = tripleClass.declarations.filterIsInstance<IrSimpleFunction>().first { it.name.asString() == "component3" }

    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        val rootParent = container as? IrDeclarationParent ?: container.parent

        irBody.transform(
            Transformer(),
            rootParent,
        )
    }

    private inner class Transformer : IrTransformer<IrDeclarationParent>() {
        override fun visitDeclaration(
            declaration: IrDeclarationBase,
            data: IrDeclarationParent,
        ): IrStatement {
            val parent = (declaration as? IrDeclarationParent) ?: data
            return super.visitDeclaration(declaration, parent)
        }

        override fun visitThrow(
            expression: IrThrow,
            data: IrDeclarationParent,
        ): IrExpression {
            expression.transformChildren(this, data)
            return buildLuaErrorCall(expression.value, expression.startOffset, expression.endOffset)
        }

        override fun visitTry(
            aTry: IrTry,
            data: IrDeclarationParent,
        ): IrExpression {
            aTry.transformChildren(this, data)

            val jumpTargets = linkedMapOf<JumpTarget, Int>()

            fun getJumpId(target: JumpTarget): Int = jumpTargets.getOrPut(target) { jumpTargets.size + 1 }

            val hasFinally = aTry.finallyExpression != null
            val singleCatch = aTry.catches.singleOrNull()

            fun buildComponentCall(
                target: IrVariable,
                comp: IrSimpleFunction,
                type: IrType,
            ): IrExpression =
                IrCallImpl(
                    startOffset = UNDEFINED_OFFSET,
                    endOffset = UNDEFINED_OFFSET,
                    type = type,
                    symbol = comp.symbol,
                    typeArgumentsCount = 0,
                ).apply {
                    dispatchReceiver = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, target.type, target.symbol)
                }

            val builder = context.createIrBuilder(data.symbolOrParent(), aTry.startOffset, aTry.endOffset)

            val tryFn =
                wrapInPCallLambda(
                    expr = aTry.tryResult,
                    parentScope = data,
                    getJumpId = ::getJumpId,
                )

            return builder
                .irBlock(aTry.startOffset, aTry.endOffset, resultType = aTry.type) {
                    val pcallRes = irTemporary(buildPCall(tryFn), nameHint = "pcall_res")
                    val okVar =
                        irTemporary(
                            value = buildComponentCall(pcallRes, comp1, irBuiltIns.booleanType),
                            nameHint = "try_ok",
                            isMutable = true,
                        )
                    val resVar =
                        irTemporary(
                            value = buildComponentCall(pcallRes, comp2, irBuiltIns.anyNType),
                            nameHint = "try_res",
                            isMutable = true,
                        )
                    val valVar =
                        irTemporary(
                            value = buildComponentCall(pcallRes, comp3, irBuiltIns.anyNType),
                            nameHint = "try_val",
                            isMutable = true,
                        )

                    if (singleCatch != null) {
                        val catchParam = singleCatch.catchParameter
                        catchParam.parent = data
                        catchParam.initializer = buildImplicitCast(irGet(resVar), catchParam.type)

                        val catchBlockExpr =
                            IrBlockImpl(
                                startOffset = singleCatch.result.startOffset,
                                endOffset = singleCatch.result.endOffset,
                                type = singleCatch.result.type,
                                origin = null,
                                statements = listOf(catchParam, singleCatch.result),
                            )

                        val wrapNotThrowable =
                            irIfThen(
                                irNot(buildIsThrowable(irGet(resVar))),
                                irSet(resVar, buildLuaInternalError(irGet(resVar))),
                            )

                        val catchThenBranch =
                            if (hasFinally) {
                                val catchFn =
                                    wrapInPCallLambda(
                                        expr = catchBlockExpr,
                                        parentScope = data,
                                        getJumpId = ::getJumpId,
                                    )
                                irBlock {
                                    +wrapNotThrowable
                                    val catchPcall = irTemporary(buildPCall(catchFn), nameHint = "catch_pcall")
                                    val catchOk =
                                        irTemporary(
                                            value = buildComponentCall(catchPcall, comp1, irBuiltIns.booleanType),
                                            nameHint = "catch_ok",
                                        )
                                    val catchRes =
                                        irTemporary(
                                            value = buildComponentCall(catchPcall, comp2, irBuiltIns.anyNType),
                                            nameHint = "catch_res",
                                        )
                                    val catchVal =
                                        irTemporary(
                                            value = buildComponentCall(catchPcall, comp3, irBuiltIns.anyNType),
                                            nameHint = "catch_val",
                                        )
                                    +irSet(okVar, irGet(catchOk))
                                    +irSet(resVar, irGet(catchRes))
                                    +irSet(valVar, irGet(catchVal))
                                }
                            } else {
                                irBlock {
                                    +wrapNotThrowable
                                    if (singleCatch.result.type.isUnit() || singleCatch.result.type.isNothing()) {
                                        +catchBlockExpr
                                    } else {
                                        +irSet(valVar, catchBlockExpr)
                                    }
                                    +irSet(okVar, irTrue())
                                    +irSet(resVar, irInt(0))
                                }
                            }

                        +irIfThen(
                            irNot(buildImplicitCast(irGet(okVar), irBuiltIns.booleanType)),
                            catchThenBranch,
                        )
                    }

                    aTry.finallyExpression?.let { finallyExpr ->
                        +finallyExpr
                    }

                    if (singleCatch == null || hasFinally) {
                        +irIfThen(
                            irNot(buildImplicitCast(irGet(okVar), irBuiltIns.booleanType)),
                            buildLuaErrorCall(irGet(resVar)),
                        )
                    }

                    for ((target, jumpId) in jumpTargets) {
                        val jumpStmt: IrExpression =
                            when (target) {
                                is JumpTarget.Return -> {
                                    val retType =
                                        when (val sym = target.symbol) {
                                            is IrReturnableBlockSymbol -> sym.owner.type
                                            else -> (sym.owner as IrFunction).returnType
                                        }
                                    val retValue =
                                        if (retType.isUnit()) {
                                            IrGetObjectValueImpl(
                                                UNDEFINED_OFFSET,
                                                UNDEFINED_OFFSET,
                                                irBuiltIns.unitType,
                                                irBuiltIns.unitClass,
                                            )
                                        } else {
                                            buildImplicitCast(irGet(valVar), retType)
                                        }
                                    IrReturnImpl(
                                        UNDEFINED_OFFSET,
                                        UNDEFINED_OFFSET,
                                        irBuiltIns.nothingType,
                                        target.symbol,
                                        retValue,
                                    )
                                }

                                is JumpTarget.Break -> {
                                    IrBreakImpl(
                                        UNDEFINED_OFFSET,
                                        UNDEFINED_OFFSET,
                                        irBuiltIns.nothingType,
                                        target.loop,
                                    ).apply {
                                        label = target.loop.label
                                    }
                                }

                                is JumpTarget.Continue -> {
                                    IrContinueImpl(
                                        UNDEFINED_OFFSET,
                                        UNDEFINED_OFFSET,
                                        irBuiltIns.nothingType,
                                        target.loop,
                                    ).apply {
                                        label = target.loop.label
                                    }
                                }
                            }

                        +irIfThen(
                            irEquals(
                                buildImplicitCast(irGet(resVar), irBuiltIns.intType),
                                irInt(jumpId),
                            ),
                            jumpStmt,
                        )
                    }

                    if (!aTry.type.isUnit() && !aTry.type.isNothing()) {
                        +buildImplicitCast(irGet(valVar), aTry.type)
                    }
                }.also {
                    it.patchDeclarationParents(data)
                }
        }
    }

    private fun IrDeclarationParent.symbolOrParent() =
        (this as? IrSymbolOwner)?.symbol
            ?: error("Expected IrSymbolOwner parent for IrTry, got $this")

    private fun wrapInPCallLambda(
        expr: IrExpression,
        parentScope: IrDeclarationParent,
        getJumpId: (JumpTarget) -> Int,
    ): IrFunctionExpression {
        val lambdaFn =
            context.irFactory
                .buildFun {
                    startOffset = UNDEFINED_OFFSET
                    endOffset = UNDEFINED_OFFSET
                    origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
                    name = Name.special("<try_fn>")
                    visibility = DescriptorVisibilities.LOCAL
                    returnType = irBuiltIns.anyNType
                }.apply {
                    parent = parentScope
                }

        val localReturnTargets = hashSetOf<IrReturnTargetSymbol>()
        val localLoops = hashSetOf<IrLoop>()
        expr.acceptVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitFunction(declaration: IrFunction) {
                    localReturnTargets += declaration.symbol
                    super.visitFunction(declaration)
                }

                override fun visitReturnableBlock(expression: IrReturnableBlock) {
                    localReturnTargets += expression.symbol
                    super.visitReturnableBlock(expression)
                }

                override fun visitLoop(loop: IrLoop) {
                    localLoops += loop
                    super.visitLoop(loop)
                }
            },
        )

        val rewrittenExpr =
            expr.transform(
                object : IrTransformer<Nothing?>() {
                    override fun visitReturn(
                        expression: IrReturn,
                        data: Nothing?,
                    ): IrExpression {
                        expression.transformChildren(this, data)
                        if (expression.returnTargetSymbol in localReturnTargets) {
                            return expression
                        }
                        val jumpId = getJumpId(JumpTarget.Return(expression.returnTargetSymbol))
                        return buildLuaMultiReturn(
                            values = listOf(irIntConst(jumpId), expression.value),
                            startOffset = expression.startOffset,
                            endOffset = expression.endOffset,
                        )
                    }

                    override fun visitBreak(
                        jump: IrBreak,
                        data: Nothing?,
                    ): IrExpression {
                        if (jump.loop in localLoops) {
                            return jump
                        }
                        val jumpId = getJumpId(JumpTarget.Break(jump.loop))
                        return buildLuaMultiReturn(
                            values = listOf(irIntConst(jumpId), irNull()),
                            startOffset = jump.startOffset,
                            endOffset = jump.endOffset,
                        )
                    }

                    override fun visitContinue(
                        jump: IrContinue,
                        data: Nothing?,
                    ): IrExpression {
                        if (jump.loop in localLoops) {
                            return jump
                        }
                        val jumpId = getJumpId(JumpTarget.Continue(jump.loop))
                        return buildLuaMultiReturn(
                            values = listOf(irIntConst(jumpId), irNull()),
                            startOffset = jump.startOffset,
                            endOffset = jump.endOffset,
                        )
                    }
                },
                null,
            )

        rewrittenExpr.patchDeclarationParents(lambdaFn)

        lambdaFn.body =
            context.irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET) {
                statements +=
                    context
                        .createIrBuilder(lambdaFn.symbol)
                        .irBlockBody(lambdaFn) {
                            when {
                                rewrittenExpr.type.isNothing() -> {
                                    +rewrittenExpr
                                }

                                rewrittenExpr.type.isUnit() -> {
                                    +rewrittenExpr
                                    +buildLuaMultiReturn(listOf(irInt(0), irNull()))
                                }

                                else -> {
                                    val resVar = irTemporary(rewrittenExpr, nameHint = "try_expr")
                                    +buildLuaMultiReturn(listOf(irInt(0), irGet(resVar)))
                                }
                            }
                        }.statements
            }

        return IrFunctionExpressionImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = irBuiltIns.functionN(0).typeWith(irBuiltIns.anyNType),
            function = lambdaFn,
            origin = IrStatementOrigin.LAMBDA,
        )
    }

    private fun buildLuaMultiReturn(
        values: List<IrExpression>,
        startOffset: Int = UNDEFINED_OFFSET,
        endOffset: Int = UNDEFINED_OFFSET,
    ): IrExpression {
        val varargExpr =
            IrVarargImpl(
                startOffset = startOffset,
                endOffset = endOffset,
                type = irBuiltIns.arrayClass.typeWith(irBuiltIns.anyNType),
                varargElementType = irBuiltIns.anyNType,
                elements = values,
            )
        return IrCallImpl(
            startOffset = startOffset,
            endOffset = endOffset,
            type = symbols.luaMultiReturn.owner.returnType,
            symbol = symbols.luaMultiReturn,
            typeArgumentsCount = 0,
        ).apply {
            arguments[0] = varargExpr
        }
    }

    private fun irIntConst(value: Int): IrExpression = IrConstImpl.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.intType, value)

    private fun buildPCall(fnExpr: IrExpression): IrExpression =
        IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = symbols.luaPCall.owner.returnType,
            symbol = symbols.luaPCall,
            typeArgumentsCount = 0,
        ).apply {
            arguments[0] = fnExpr
        }

    private fun buildLuaErrorCall(
        value: IrExpression,
        startOffset: Int = UNDEFINED_OFFSET,
        endOffset: Int = UNDEFINED_OFFSET,
    ): IrExpression =
        IrCallImpl(
            startOffset = startOffset,
            endOffset = endOffset,
            type = irBuiltIns.nothingType,
            symbol = symbols.luaError,
            typeArgumentsCount = 0,
        ).apply {
            arguments[0] = value
        }

    private fun buildIsThrowable(value: IrExpression): IrExpression =
        IrTypeOperatorCallImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            irBuiltIns.booleanType,
            IrTypeOperator.INSTANCEOF,
            irBuiltIns.throwableType,
            value,
        )

    private fun buildLuaInternalError(rawError: IrExpression): IrExpression {
        val ctor =
            symbols.luaInternalError.owner.constructors
                .single()
        return IrConstructorCallImpl
            .fromSymbolOwner(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                type = symbols.luaInternalError.owner.defaultType,
                constructorSymbol = ctor.symbol,
            ).apply {
                arguments[0] = rawError
            }
    }

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

    private fun irNull(): IrExpression = IrConstImpl.constNull(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irBuiltIns.nothingNType)
}
