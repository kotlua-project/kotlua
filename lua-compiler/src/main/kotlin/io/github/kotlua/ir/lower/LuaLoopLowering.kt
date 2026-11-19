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
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrBreak
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrContinue
import org.jetbrains.kotlin.ir.expressions.IrDoWhileLoop
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrReturnableBlock
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrBreakImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDoWhileLoopImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.symbols.IrReturnableBlockSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.transformStatement
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.transformFlat
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.Name

class LuaLoopLowering(
    private val context: LuaIrBackendContext,
) : BodyLoweringPass {
    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        if (container !is IrFunction) return
        lowerFunctionBody(irBody, container)
        irBody.patchDeclarationParents(container)
    }

    private fun lowerFunctionBody(
        irBody: IrBody,
        function: IrFunction,
    ) {
        val loopsNeedingContinueWrapper = collectLoopsWithContinue(irBody)
        val analysis = analyzeBarriers(irBody, loopsNeedingContinueWrapper)

        val transformer = FunctionLoopTransformer(context, function, analysis)
        irBody.transformChildren(transformer, null)

        val jumpVar = transformer.jumpVarOrNull
        if (jumpVar != null && irBody is IrBlockBody) {
            irBody.statements.add(0, jumpVar)
        }
    }

    private fun collectLoopsWithContinue(irBody: IrBody): Set<IrLoop> {
        val result = hashSetOf<IrLoop>()
        irBody.acceptChildrenVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitFunction(declaration: IrFunction) {
                }

                override fun visitContinue(jump: IrContinue) {
                    result += jump.loop
                    super.visitContinue(jump)
                }
            },
        )
        return result
    }

    private fun analyzeBarriers(
        irBody: IrBody,
        loopsNeedingContinueWrapper: Set<IrLoop>,
    ): FunctionJumpAnalysis {
        val barrierStack = mutableListOf<BarrierKey>()
        val barrierInfos = linkedMapOf<BarrierKey, BarrierAnalysisInfo>()
        var nextJumpId = 1

        fun infoFor(key: BarrierKey): BarrierAnalysisInfo = barrierInfos.getOrPut(key) { BarrierAnalysisInfo() }

        fun recordJump(targetKey: BarrierKey) {
            val targetIdx = barrierStack.indexOfLast { it == targetKey }
            check(targetIdx >= 0) { "Jump target barrier $targetKey not found in active stack $barrierStack" }

            val innermostIdx = barrierStack.lastIndex
            if (targetIdx == innermostIdx) return

            val targetInfo = infoFor(targetKey)
            if (targetInfo.stopId == null) {
                targetInfo.stopId = nextJumpId++
            }
            for (i in (targetIdx + 1)..innermostIdx) {
                infoFor(barrierStack[i]).propagateToParent = true
            }
        }

        irBody.acceptChildrenVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitFunction(declaration: IrFunction) {
                }

                override fun visitWhileLoop(loop: IrWhileLoop) {
                    visitLoop(loop)
                }

                override fun visitDoWhileLoop(loop: IrDoWhileLoop) {
                    visitLoop(loop)
                }

                override fun visitLoop(loop: IrLoop) {
                    val loopKey = BarrierKey.Loop(loop)
                    barrierStack.add(loopKey)
                    val hasContinueWrapper = loop in loopsNeedingContinueWrapper
                    if (hasContinueWrapper) {
                        barrierStack.add(BarrierKey.ContinueWrapper(loop))
                    }
                    loop.body?.acceptChildrenVoid(this)
                    if (hasContinueWrapper) {
                        barrierStack.removeLast()
                    }
                    barrierStack.removeLast()
                    loop.condition.acceptChildrenVoid(this)
                }

                override fun visitBlock(expression: IrBlock) {
                    if (expression !is IrReturnableBlock) {
                        super.visitBlock(expression)
                        return
                    }
                    val key = BarrierKey.Returnable(expression.symbol)
                    barrierStack.add(key)
                    super.visitBlock(expression)
                    barrierStack.removeLast()
                }

                override fun visitBreak(jump: IrBreak) {
                    recordJump(BarrierKey.Loop(jump.loop))
                    super.visitBreak(jump)
                }

                override fun visitContinue(jump: IrContinue) {
                    recordJump(BarrierKey.ContinueWrapper(jump.loop))
                    super.visitContinue(jump)
                }

                override fun visitReturn(expression: IrReturn) {
                    val targetSymbol = expression.returnTargetSymbol
                    if (targetSymbol is IrReturnableBlockSymbol) {
                        recordJump(BarrierKey.Returnable(targetSymbol))
                    }
                    super.visitReturn(expression)
                }
            },
        )

        return FunctionJumpAnalysis(
            loopsNeedingContinueWrapper = loopsNeedingContinueWrapper,
            barrierInfos = barrierInfos,
        )
    }

    private sealed interface BarrierKey {
        data class Loop(
            val loop: IrLoop,
        ) : BarrierKey

        data class ContinueWrapper(
            val loop: IrLoop,
        ) : BarrierKey

        data class Returnable(
            val symbol: IrReturnableBlockSymbol,
        ) : BarrierKey
    }

    private class BarrierAnalysisInfo(
        var stopId: Int? = null,
        var propagateToParent: Boolean = false,
    )

    private class FunctionJumpAnalysis(
        val loopsNeedingContinueWrapper: Set<IrLoop>,
        val barrierInfos: Map<BarrierKey, BarrierAnalysisInfo>,
    )

    private sealed class ActiveBarrier {
        class Loop(
            val irLoop: IrLoop,
        ) : ActiveBarrier()

        class Returnable(
            val symbol: IrReturnableBlockSymbol,
        ) : ActiveBarrier()
    }

    private inner class FunctionLoopTransformer(
        private val context: LuaIrBackendContext,
        private val function: IrFunction,
        private val analysis: FunctionJumpAnalysis,
    ) : IrElementTransformerVoid() {
        private val intType = context.irBuiltIns.intType
        private val booleanType = context.irBuiltIns.booleanType
        private val unitType = context.irBuiltIns.unitType
        private val unitValue
            get() = IrGetObjectValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unitType, context.irBuiltIns.unitClass)
        private val constFalse
            get() = IrConstImpl.boolean(UNDEFINED_OFFSET, UNDEFINED_OFFSET, booleanType, false)

        private val activeBarriers = mutableListOf<ActiveBarrier>()
        private val keyToActiveBarrier = hashMapOf<BarrierKey, ActiveBarrier>()

        var jumpVarOrNull: IrVariable? = null
            private set

        private fun getOrCreateJumpVar(): IrVariable =
            jumpVarOrNull ?: IrVariableImpl(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                symbol = IrVariableSymbolImpl(),
                name = Name.identifier($$"tmp$jump"),
                type = intType,
                isVar = true,
                isConst = false,
                isLateinit = false,
            ).apply {
                parent = function
                initializer = constInt(0)
                jumpVarOrNull = this
            }

        private fun constInt(value: Int) = IrConstImpl.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, intType, value)

        private fun getJumpVar(): IrExpression =
            IrGetValueImpl(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                type = intType,
                symbol = getOrCreateJumpVar().symbol,
            )

        private fun setJumpVar(value: Int): IrStatement =
            IrSetValueImpl(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                type = unitType,
                symbol = getOrCreateJumpVar().symbol,
                value = constInt(value),
                origin = null,
            )

        private fun buildExitStatement(barrier: ActiveBarrier): IrStatement =
            when (barrier) {
                is ActiveBarrier.Loop -> {
                    IrBreakImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unitType, barrier.irLoop)
                }

                is ActiveBarrier.Returnable -> {
                    IrReturnImpl(
                        startOffset = UNDEFINED_OFFSET,
                        endOffset = UNDEFINED_OFFSET,
                        type = unitType,
                        returnTargetSymbol = barrier.symbol,
                        value = unitValue,
                    )
                }
            }

        private fun buildIntComparison(
            lhs: IrExpression,
            rhs: IrExpression,
            isEquals: Boolean,
        ): IrExpression =
            IrCallImpl(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                type = booleanType,
                symbol = if (isEquals) context.symbols.luaEq else context.symbols.luaNeq,
                typeArgumentsCount = 0,
            ).apply {
                arguments[0] = lhs
                arguments[1] = rhs
            }

        private fun buildIf(
            condition: IrExpression,
            thenStatement: IrStatement,
        ): IrStatement {
            val thenBlock =
                IrBlockImpl(
                    startOffset = UNDEFINED_OFFSET,
                    endOffset = UNDEFINED_OFFSET,
                    type = unitType,
                    origin = null,
                    statements = listOf(thenStatement),
                )
            return IrWhenImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unitType, null).apply {
                branches += IrBranchImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, condition, thenBlock)
            }
        }

        private fun buildPostBarrierStatements(key: BarrierKey): List<IrStatement> {
            val info = analysis.barrierInfos[key] ?: return emptyList()
            val result = mutableListOf<IrStatement>()

            val stopId = info.stopId
            if (stopId != null) {
                result +=
                    buildIf(
                        condition = buildIntComparison(getJumpVar(), constInt(stopId), isEquals = true),
                        thenStatement = setJumpVar(0),
                    )
            }

            if (info.propagateToParent) {
                val parentBarrier =
                    activeBarriers.lastOrNull()
                        ?: error("propagateToParent is set for $key, but there is no enclosing barrier")
                result +=
                    buildIf(
                        condition = buildIntComparison(getJumpVar(), constInt(0), isEquals = false),
                        thenStatement = buildExitStatement(parentBarrier),
                    )
            }

            return result
        }

        private fun wrapWithPostStatements(
            statement: IrStatement,
            postStatements: List<IrStatement>,
        ): IrStatement {
            if (postStatements.isEmpty()) return statement
            return IrCompositeImpl(
                startOffset = statement.startOffset,
                endOffset = statement.endOffset,
                type = unitType,
                origin = null,
                statements = listOf(statement) + postStatements,
            )
        }

        private fun destructureComposites(statements: MutableList<IrStatement>) {
            statements.transformFlat { stmt ->
                val transformed = stmt.transformStatement(this)
                if (transformed is IrComposite) transformed.statements else listOf(transformed)
            }
        }

        override fun visitFunction(declaration: IrFunction): IrStatement {
            if (declaration !== function) {
                declaration.body?.let { lowerFunctionBody(it, declaration) }
                return declaration
            }
            return super.visitFunction(declaration)
        }

        override fun visitBlockBody(body: IrBlockBody): IrBody {
            destructureComposites(body.statements)
            return body
        }

        override fun visitContainerExpression(expression: IrContainerExpression): IrExpression {
            if (expression is IrReturnableBlock) {
                val key = BarrierKey.Returnable(expression.symbol)
                val barrier = ActiveBarrier.Returnable(expression.symbol)
                activeBarriers.add(barrier)
                keyToActiveBarrier[key] = barrier

                destructureComposites(expression.statements)

                keyToActiveBarrier.remove(key)
                activeBarriers.removeLast()

                val postStatements = buildPostBarrierStatements(key)
                return wrapWithPostStatements(expression, postStatements) as IrExpression
            }

            destructureComposites(expression.statements)
            return expression
        }

        override fun visitWhileLoop(loop: IrWhileLoop): IrExpression = transformLoop(loop)

        override fun visitDoWhileLoop(loop: IrDoWhileLoop): IrExpression = transformLoop(loop)

        private fun transformLoop(loop: IrLoop): IrExpression {
            val transformedCondition = loop.condition.transform(this, null)
            loop.condition = transformedCondition

            val loopKey = BarrierKey.Loop(loop)
            val loopBarrier = ActiveBarrier.Loop(loop)
            activeBarriers.add(loopBarrier)
            keyToActiveBarrier[loopKey] = loopBarrier

            if (loop in analysis.loopsNeedingContinueWrapper) {
                val continueKey = BarrierKey.ContinueWrapper(loop)
                val wrapperLoop =
                    IrDoWhileLoopImpl(
                        startOffset = loop.body?.startOffset ?: loop.startOffset,
                        endOffset = loop.body?.endOffset ?: loop.endOffset,
                        type = unitType,
                        origin = loop.origin,
                    ).apply {
                        condition = constFalse
                        body = loop.body
                    }

                val continueBarrier = ActiveBarrier.Loop(wrapperLoop)
                activeBarriers.add(continueBarrier)
                keyToActiveBarrier[continueKey] = continueBarrier

                wrapperLoop.body = wrapperLoop.body?.transform(this, null)

                keyToActiveBarrier.remove(continueKey)
                activeBarriers.removeLast()

                val postContinueStatements = buildPostBarrierStatements(continueKey)
                loop.body =
                    if (postContinueStatements.isEmpty()) {
                        wrapperLoop
                    } else {
                        IrBlockImpl(
                            startOffset = wrapperLoop.startOffset,
                            endOffset = wrapperLoop.endOffset,
                            type = unitType,
                            origin = null,
                            statements = listOf(wrapperLoop) + postContinueStatements,
                        )
                    }
            } else {
                loop.body = loop.body?.transform(this, null)
            }

            keyToActiveBarrier.remove(loopKey)
            activeBarriers.removeLast()

            val postLoopStatements = buildPostBarrierStatements(loopKey)
            return wrapWithPostStatements(loop, postLoopStatements) as IrExpression
        }

        override fun visitBreak(jump: IrBreak): IrExpression = translateJump(BarrierKey.Loop(jump.loop), jump)

        override fun visitContinue(jump: IrContinue): IrExpression = translateJump(BarrierKey.ContinueWrapper(jump.loop), jump)

        override fun visitReturn(expression: IrReturn): IrExpression {
            val targetSymbol = expression.returnTargetSymbol
            if (targetSymbol !is IrReturnableBlockSymbol) {
                return super.visitReturn(expression)
            }
            expression.transformChildren(this, null)
            return translateJump(BarrierKey.Returnable(targetSymbol), expression)
        }

        private fun translateJump(
            targetKey: BarrierKey,
            originalExpression: IrExpression,
        ): IrExpression {
            val targetBarrier =
                keyToActiveBarrier[targetKey]
                    ?: compilationException("Unmapped jump target barrier $targetKey", originalExpression)
            val innermostBarrier =
                activeBarriers.lastOrNull()
                    ?: compilationException("Jump outside of any barrier", originalExpression)

            if (targetBarrier === innermostBarrier) {
                return buildExitStatement(innermostBarrier) as IrExpression
            }

            val stopId =
                analysis.barrierInfos[targetKey]?.stopId
                    ?: compilationException("Missing stopId for crossing jump to $targetKey", originalExpression)

            return IrCompositeImpl(
                startOffset = originalExpression.startOffset,
                endOffset = originalExpression.endOffset,
                type = originalExpression.type,
                origin = null,
                statements =
                    listOf(
                        setJumpVar(stopId),
                        buildExitStatement(innermostBarrier),
                    ),
            )
        }
    }
}
