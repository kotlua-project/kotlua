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
package io.github.kotlua.ir.lower.coroutines

import io.github.kotlua.ir.LuaIrBackendContext
import org.jetbrains.kotlin.backend.common.ir.isPure
import org.jetbrains.kotlin.backend.common.lower.FINALLY_EXPRESSION
import org.jetbrains.kotlin.backend.common.peek
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrBreak
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrCatch
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrContinue
import org.jetbrains.kotlin.ir.expressions.IrDoWhileLoop
import org.jetbrains.kotlin.ir.expressions.IrDynamicMemberExpression
import org.jetbrains.kotlin.ir.expressions.IrDynamicOperatorExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetClass
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.expressions.IrThrow
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCatchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrContinueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrElseBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSpreadElementImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrStringConcatenationImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrThrowImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrReturnableBlockSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrDynamicType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.isElseBranch
import org.jetbrains.kotlin.ir.util.isSuspend
import org.jetbrains.kotlin.ir.util.previousOffset
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue

class SuspendState(
    type: IrType,
) {
    val entryBlock: IrContainerExpression = IrCompositeImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type)
    val successors = mutableSetOf<SuspendState>()
    var id: Int = -1
    var hasPredecessors: Boolean = false
}

private var IrComposite.suspendState: SuspendState? by irAttribute(copyByDefault = false)

data class LoopBounds(
    val headState: SuspendState,
    val exitState: SuspendState,
)

data class TryState(
    val tryState: SuspendState,
    val catchState: SuspendState,
)

private fun createDispatchPoint(target: SuspendState): IrComposite =
    IrCompositeImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, target.entryBlock.type).also { it.suspendState = target }

class DispatchPointTransformer(
    val action: (SuspendState) -> IrExpression,
) : IrElementTransformerVoid() {
    override fun visitComposite(expression: IrComposite): IrExpression {
        val suspendState = expression.suspendState ?: return super.visitComposite(expression)
        return action(suspendState)
    }
}

class StateMachineBuilder(
    private val suspendableNodes: MutableSet<IrElement>,
    val context: LuaIrBackendContext,
    val function: IrFunctionSymbol,
    private val rootLoop: IrLoop,
    private val exceptionSymbolGetter: IrSimpleFunction,
    private val exceptionSymbolSetter: IrSimpleFunction,
    private val exStateSymbolGetter: IrSimpleFunction,
    private val exStateSymbolSetter: IrSimpleFunction,
    private val stateSymbolSetter: IrSimpleFunction,
    private val thisSymbol: IrValueParameterSymbol,
    private val getSuspendResultAsType: (IrType) -> IrExpression,
    private val setSuspendResultValue: (IrExpression) -> IrStatement,
) : IrVisitorVoid() {
    private val loopMap = hashMapOf<IrLoop, LoopBounds>()
    private val unit = context.irBuiltIns.unitType
    private val anyN = context.irBuiltIns.anyNType
    private val nothing = context.irBuiltIns.nothingType
    private val booleanNotSymbol = context.irBuiltIns.booleanNotSymbol
    private val eqeqeqSymbol = context.irBuiltIns.eqeqeqSymbol

    private val thisReceiver get() = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, thisSymbol.owner.type, thisSymbol)

    val entryState = SuspendState(unit)
    val rootExceptionTrap = buildExceptionTrapState()
    val allTheIntermediateLocals = mutableListOf<IrVariable>()
    private val globalExceptionVar = buildVar(exceptionSymbolGetter.returnType.makeNotNull(), "e")
    lateinit var globalCatch: IrCatch
    var hasTryCatch = false
        private set
    private var hasExceptions = false

    fun finalizeStateMachine() {
        if (!isBlockEnded() && currentBlock.statements.lastOrNull() !is IrReturn) {
            addStatement(
                IrReturnImpl(
                    startOffset = rootLoop.endOffset.previousOffset,
                    endOffset = rootLoop.endOffset.previousOffset,
                    type = nothing,
                    returnTargetSymbol = function,
                    value = unitValue,
                ),
            )
        }
        if (hasTryCatch) {
            globalCatch = buildGlobalCatch()
        }
        simplifyStates()
        if (hasTryCatch) {
            entryState.successors += rootExceptionTrap
        }
    }

    private fun buildGlobalCatch(): IrCatch {
        val catchVariable = globalExceptionVar
        val globalExceptionSymbol = globalExceptionVar.symbol
        val block = IrBlockImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unit)
        if (hasExceptions) {
            val thenBlock = IrBlockImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unit)
            val elseBlock = IrBlockImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unit)
            val check =
                buildCall(eqeqeqSymbol).apply {
                    arguments[0] = exceptionState()
                    arguments[1] = createDispatchPoint(rootExceptionTrap)
                }
            block.statements += buildIfElse(unit, check, thenBlock, elseBlock)
            thenBlock.statements +=
                IrThrowImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    nothing,
                    IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, catchVariable.type, globalExceptionSymbol),
                )

            elseBlock.statements +=
                buildCall(stateSymbolSetter.symbol, unit).apply {
                    arguments[0] = thisReceiver
                    arguments[1] = exceptionState()
                }
            elseBlock.statements +=
                buildCall(exceptionSymbolSetter.symbol, unit).apply {
                    arguments[0] = thisReceiver
                    arguments[1] = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, catchVariable.type, globalExceptionSymbol)
                }
        } else {
            block.statements +=
                IrThrowImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    nothing,
                    IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, catchVariable.type, globalExceptionSymbol),
                )
        }

        return IrCatchImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, catchVariable, block)
    }

    private var currentState = entryState
    private var currentBlock = entryState.entryBlock

    private val catchBlockStack = mutableListOf(rootExceptionTrap)
    private val tryStateMap = hashMapOf<IrExpression, TryState>()
    private val tryLoopStack = mutableListOf<IrExpression>()

    private fun buildExceptionTrapState(): SuspendState {
        val state = SuspendState(unit)
        state.entryBlock.statements += IrThrowImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, nothing, pendingException())
        return state
    }

    private fun newState() {
        val newState = SuspendState(unit)
        doDispatch(newState)
        updateState(newState)
    }

    private fun updateState(newState: SuspendState) {
        currentState = newState
        currentBlock = newState.entryBlock
    }

    private fun lastExpression() = currentBlock.statements.lastOrNull() as? IrExpression ?: unitValue

    private fun IrContainerExpression.addStatement(statement: IrStatement) {
        statements.add(statement)
    }

    private fun addStatement(statement: IrStatement) = currentBlock.addStatement(statement)

    private fun isBlockEnded(): Boolean {
        val lastExpression = currentBlock.statements.lastOrNull() as? IrExpression ?: return false
        return lastExpression.type.isNothing()
    }

    private fun maybeDoDispatch(target: SuspendState): Boolean {
        if (!isBlockEnded()) {
            doDispatch(target)
            return true
        }
        return false
    }

    private fun doDispatch(
        target: SuspendState,
        andContinue: Boolean = true,
    ) = doDispatchImpl(target, currentBlock, andContinue)

    private fun doDispatchImpl(
        target: SuspendState,
        block: IrContainerExpression,
        andContinue: Boolean,
    ) {
        val irDispatch = createDispatchPoint(target)
        currentState.successors.add(target)
        target.hasPredecessors = true
        block.addStatement(
            buildCall(stateSymbolSetter.symbol, unit).apply {
                arguments[0] = thisReceiver
                arguments[1] = irDispatch
            },
        )
        if (andContinue) doContinue(block)
    }

    private fun doContinue(block: IrContainerExpression = currentBlock) {
        block.addStatement(IrContinueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, nothing, rootLoop).apply { label = rootLoop.label })
    }

    private fun transformLastExpression(transformer: (IrExpression) -> IrStatement) {
        val expression = lastExpression()
        val newStatement = transformer(expression)
        currentBlock.statements.let { if (it.isNotEmpty()) it[it.lastIndex] = newStatement else it += newStatement }
    }

    private fun buildDispatchBlock(target: SuspendState) =
        IrCompositeImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unit).also { doDispatchImpl(target, it, true) }

    override fun visitElement(element: IrElement) {
        if (isBlockEnded()) return
        if (element in suspendableNodes) {
            element.acceptChildrenVoid(this)
        } else {
            addStatement(element as IrStatement)
        }
    }

    private fun transformLoop(
        loop: IrLoop,
        transformer: (IrLoop, SuspendState, SuspendState) -> Unit,
    ) {
        if (loop !in suspendableNodes) return addStatement(loop)

        newState()

        val loopHeadState = currentState
        val loopExitState = SuspendState(unit)

        loopMap[loop] = LoopBounds(loopHeadState, loopExitState)

        tryLoopStack.push(loop)
        transformer(loop, loopHeadState, loopExitState)
        tryLoopStack.pop().also { assert(it === loop) }

        loopMap.remove(loop)
        if (loopExitState.hasPredecessors) {
            updateState(loopExitState)
        }
    }

    override fun visitWhileLoop(loop: IrWhileLoop) =
        transformLoop(loop) { l, head, exit ->
            if (isBlockEnded()) return@transformLoop
            l.condition.acceptVoid(this)

            transformLastExpression {
                val exitCond = buildCall(booleanNotSymbol).apply { dispatchReceiver = it }
                val irBreak = buildDispatchBlock(exit)
                buildIfElse(unit, exitCond, irBreak)
            }

            l.body?.acceptVoid(this)
            maybeDoDispatch(head)
        }

    override fun visitDoWhileLoop(loop: IrDoWhileLoop) {
        if (isBlockEnded()) return
        if ((loop.condition as? IrConst)?.value == false && !hasContinueToLoop(loop)) {
            if (loop !in suspendableNodes) return addStatement(loop)

            val loopExitState = SuspendState(unit)
            loopMap[loop] = LoopBounds(loopExitState, loopExitState)

            tryLoopStack.push(loop)
            loop.body?.acceptVoid(this)
            maybeDoDispatch(loopExitState)
            tryLoopStack.pop().also { assert(it === loop) }

            loopMap.remove(loop)
            if (loopExitState.hasPredecessors) {
                updateState(loopExitState)
            }
            return
        }

        transformLoop(loop) { l, head, exit ->
            l.body?.acceptVoid(this)
            if (!isBlockEnded()) {
                l.condition.acceptVoid(this)

                transformLastExpression {
                    val irContinue = buildDispatchBlock(head)
                    buildIfElse(unit, it, irContinue)
                }

                doDispatch(exit)
            }
        }
    }

    private fun hasContinueToLoop(loop: IrLoop): Boolean {
        var found = false
        loop.body?.acceptVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    if (!found) element.acceptChildrenVoid(this)
                }

                override fun visitFunction(declaration: IrFunction) = Unit

                override fun visitClass(declaration: IrClass) = Unit

                override fun visitContinue(jump: IrContinue) {
                    if (jump.loop === loop) found = true
                    super.visitContinue(jump)
                }
            },
        )
        return found
    }

    private fun implicitCast(
        value: IrExpression,
        toType: IrType,
    ) = IrTypeOperatorCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, toType, IrTypeOperator.IMPLICIT_CAST, toType, value)

    override fun visitCall(expression: IrCall) {
        if (isBlockEnded()) return
        super.visitCall(expression)
        if (isBlockEnded()) return

        if (expression.isSuspend) {
            val result = lastExpression()
            val continueState = SuspendState(unit)
            val dispatch = createDispatchPoint(continueState)

            currentState.successors += continueState

            transformLastExpression {
                buildCall(stateSymbolSetter.symbol, unit).apply {
                    arguments[0] = thisReceiver
                    arguments[1] = dispatch
                }
            }

            addStatement(setSuspendResultValue(result))

            val irReturn = IrReturnImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, nothing, function, getSuspendResultAsType(anyN))
            val check =
                buildCall(eqeqeqSymbol).apply {
                    arguments[0] = getSuspendResultAsType(anyN)
                    arguments[1] = buildCall(context.symbols.coroutineSuspendedGetter)
                }

            val suspensionBlock = IrBlockImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unit, null, listOf(irReturn))
            addStatement(buildIfElse(unit, check, suspensionBlock))

            doContinue()

            updateState(continueState)
            addStatement(getSuspendResultAsType(expression.type))
        }
    }

    override fun visitBreak(jump: IrBreak) {
        if (isBlockEnded()) return
        val exitState = loopMap[jump.loop]!!.exitState
        resetExceptionStateIfNeeded(jump.loop)
        doDispatch(exitState)
    }

    override fun visitContinue(jump: IrContinue) {
        if (isBlockEnded()) return
        val headState = loopMap[jump.loop]!!.headState
        resetExceptionStateIfNeeded(jump.loop)
        doDispatch(headState)
    }

    private fun resetExceptionStateIfNeeded(loop: IrLoop) {
        var nearestTry: IrExpression? = null
        var found = false
        var needReset = false
        for (e in tryLoopStack.asReversed()) {
            if (e is IrTry) {
                needReset = !found
            }
            if (e === loop) {
                found = true
            }
            if (found && e is IrTry) {
                nearestTry = e
                break
            }
        }

        if (needReset) {
            val tryState = tryStateMap[nearestTry]?.catchState ?: rootExceptionTrap
            setupExceptionState(tryState)
        }
    }

    private fun wrap(
        expression: IrExpression,
        variable: IrVariableSymbol,
    ) = IrSetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unit, variable, expression, null)

    override fun visitComposite(expression: IrComposite) {
        if (isBlockEnded()) return
        if (expression.origin == FINALLY_EXPRESSION) {
            catchBlockStack.peek()?.let(::setupExceptionState)
        }
        super.visitComposite(expression)
    }

    override fun visitWhen(expression: IrWhen) {
        if (isBlockEnded()) return
        if (expression !in suspendableNodes) return addStatement(expression)

        val exitState = SuspendState(expression.type)
        val varSymbol: IrVariableSymbol?
        val branches: List<IrBranch>

        if (hasResultingValue(expression)) {
            val irVar = tempVar(expression.type, "WHEN_RESULT")
            varSymbol = irVar.symbol
            addStatement(irVar)

            branches =
                expression.branches.map {
                    val wrapped =
                        if (it.result.type.isNothing()) {
                            it.result
                        } else {
                            wrap(it.result, varSymbol).also { w ->
                                if (it.result in suspendableNodes) {
                                    suspendableNodes += w
                                }
                            }
                        }
                    when {
                        isElseBranch(it) -> IrElseBranchImpl(it.startOffset, it.endOffset, it.condition, wrapped)
                        else -> IrBranchImpl(it.startOffset, it.endOffset, it.condition, wrapped)
                    }
                }
        } else {
            varSymbol = null
            branches = expression.branches
        }

        var exitStateDispatched = false
        for (branch in branches) {
            if (!isElseBranch(branch)) {
                branch.condition.acceptVoid(this)
                val branchBlock = IrCompositeImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, branch.result.type)
                val elseBlock = IrCompositeImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, expression.type)

                val dispatchState = currentState
                transformLastExpression {
                    buildIfElse(unit, it, branchBlock, elseBlock)
                }

                currentBlock = branchBlock
                branch.result.acceptVoid(this)

                maybeDoDispatch(exitState).ifTrue { exitStateDispatched = true }

                currentState = dispatchState
                currentBlock = elseBlock
            } else {
                branch.result.acceptVoid(this)
                maybeDoDispatch(exitState).ifTrue { exitStateDispatched = true }
                break
            }
        }

        maybeDoDispatch(exitState).ifTrue { exitStateDispatched = true }

        if (exitStateDispatched) {
            updateState(exitState)
        }

        if (varSymbol != null) {
            addStatement(IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, varSymbol.owner.type, varSymbol))
        }
    }

    override fun visitSetValue(expression: IrSetValue) {
        if (isBlockEnded()) return
        if (expression !in suspendableNodes) return addStatement(expression)
        expression.acceptChildrenVoid(this)
        if (!isBlockEnded()) {
            transformLastExpression { expression.apply { value = it } }
        }
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall) {
        if (isBlockEnded()) return
        if (expression !in suspendableNodes) return addStatement(expression)
        expression.acceptChildrenVoid(this)
        if (!isBlockEnded()) {
            transformLastExpression { expression.apply { argument = it } }
        }
    }

    override fun visitVariable(declaration: IrVariable) {
        if (isBlockEnded()) return
        registerLocal(declaration)

        val initializer = declaration.initializer
        declaration.initializer = null

        val startOffset = declaration.startOffset
        val endOffset = declaration.endOffset
        declaration.startOffset = UNDEFINED_OFFSET
        declaration.endOffset = UNDEFINED_OFFSET

        if (declaration !in suspendableNodes) {
            initializer?.let { addStatement(IrSetValueImpl(startOffset, endOffset, unit, declaration.symbol, it, null)) }
            return
        }

        initializer?.acceptVoid(this)
        if (!isBlockEnded()) {
            transformLastExpression { IrSetValueImpl(startOffset, endOffset, unit, declaration.symbol, it, null) }
        }
    }

    override fun visitGetField(expression: IrGetField) {
        if (isBlockEnded()) return
        if (expression !in suspendableNodes) return addStatement(expression)
        expression.acceptChildrenVoid(this)
        transformLastExpression { expression.apply { receiver = it } }
    }

    override fun visitDynamicMemberExpression(
        expression: IrDynamicMemberExpression,
        data: Nothing?,
    ) {
        if (isBlockEnded()) return
        if (expression !in suspendableNodes) return addStatement(expression)
        expression.acceptChildrenVoid(this)
        transformLastExpression { expression.apply { receiver = it } }
    }

    override fun visitDynamicOperatorExpression(expression: IrDynamicOperatorExpression) {
        if (isBlockEnded()) return
        if (expression !in suspendableNodes) return addStatement(expression)
        transformArguments(expression.arguments)
        addStatement(expression)
    }

    override fun visitGetClass(expression: IrGetClass) {
        if (isBlockEnded()) return
        if (expression !in suspendableNodes) return addStatement(expression)
        expression.acceptChildrenVoid(this)
        transformLastExpression { expression.apply { argument = it } }
    }

    override fun visitVararg(expression: IrVararg) {
        if (isBlockEnded()) return
        if (expression !in suspendableNodes) return addStatement(expression)
        val spreadIndices = mutableSetOf<Int>()
        val newArgs =
            expression.elements
                .mapIndexedTo(mutableListOf()) { index, item ->
                    if (item is IrSpreadElement) {
                        spreadIndices.add(index)
                        item.expression
                    } else {
                        item as IrExpression
                    }
                }.apply(this::transformArguments)
                .mapIndexed { index, item ->
                    if (index in spreadIndices) {
                        IrSpreadElementImpl(item.startOffset, item.endOffset, item)
                    } else {
                        item
                    }
                }
        addStatement(
            IrVarargImpl(
                expression.startOffset,
                expression.endOffset,
                expression.type,
                expression.varargElementType,
                newArgs,
            ),
        )
    }

    private inline fun <reified E : IrExpression?> transformArguments(arguments: MutableList<E>) {
        var suspendableCount = arguments.fold(0) { r, n -> if (n != null && n in suspendableNodes) r + 1 else r }
        arguments.replaceAll { arg ->
            if (arg.isPure(false)) {
                arg
            } else {
                require(arg != null)
                if (suspendableCount > 0) {
                    if (arg in suspendableNodes) suspendableCount--
                    arg.acceptVoid(this)
                    val irVar = tempVar(arg.type, "ARGUMENT")
                    transformLastExpression {
                        IrSetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unit, irVar.symbol, it, null)
                    }
                    @Suppress("UNCHECKED_CAST")
                    IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irVar.type, irVar.symbol) as E
                } else {
                    arg.deepCopyWithSymbols(function.owner)
                }
            }
        }
    }

    override fun visitMemberAccess(expression: IrMemberAccessExpression<*>) {
        if (isBlockEnded()) return
        if (expression !in suspendableNodes) {
            addExceptionEdge()
            return addStatement(expression)
        }

        transformArguments(expression.arguments)
        addExceptionEdge()
        addStatement(expression)
    }

    override fun visitSetField(expression: IrSetField) {
        if (isBlockEnded()) return
        if (expression !in suspendableNodes) return addStatement(expression)

        val newArguments = mutableListOf(expression.receiver, expression.value).also(this::transformArguments)
        val receiver = newArguments[0]
        val value = newArguments[1]!!

        addStatement(
            expression.run {
                IrSetFieldImpl(
                    startOffset,
                    endOffset,
                    symbol,
                    receiver,
                    value,
                    unit,
                    origin,
                    superQualifierSymbol,
                )
            },
        )
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation) {
        if (isBlockEnded()) return
        if (expression !in suspendableNodes) return addStatement(expression)

        val newArguments = expression.arguments.toMutableList().apply(this::transformArguments)
        addStatement(
            expression.run {
                IrStringConcatenationImpl(startOffset, endOffset, type, newArguments)
            },
        )
    }

    private val unitValue get() = IrGetObjectValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unit, context.irBuiltIns.unitClass)

    override fun visitReturn(expression: IrReturn) {
        if (isBlockEnded()) return
        expression.acceptChildrenVoid(this)
        val returnTarget = expression.returnTargetSymbol
        if (returnTarget !is IrReturnableBlockSymbol) {
            transformLastExpression { expression.apply { value = it } }
        }
    }

    private fun addExceptionEdge() {
        hasExceptions = true
        val trap = catchBlockStack.peek()!!
        if (trap !== rootExceptionTrap) {
            currentState.successors += trap
            trap.hasPredecessors = true
        }
    }

    private fun hasResultingValue(expression: IrExpression) = !expression.type.run { isNothing() || isUnit() }

    override fun visitThrow(expression: IrThrow) {
        if (isBlockEnded()) return
        expression.acceptChildrenVoid(this)
        addExceptionEdge()
        transformLastExpression { expression.apply { value = it } }
    }

    override fun visitTry(aTry: IrTry) {
        if (isBlockEnded()) return
        require(aTry.finallyExpression == null)
        hasTryCatch = true

        val tryState = buildTryState()
        val enclosingCatch = catchBlockStack.peek()!!

        tryStateMap[aTry] = tryState
        catchBlockStack.push(tryState.catchState)
        tryLoopStack.push(aTry)

        val exitState = SuspendState(unit)
        val varSymbol = if (hasResultingValue(aTry)) tempVar(aTry.type, "TRY_RESULT") else null

        if (varSymbol != null) {
            addStatement(varSymbol)
        }

        setupExceptionState(tryState.catchState)

        val tryResult =
            if (varSymbol != null && !aTry.tryResult.type.isNothing()) {
                wrap(aTry.tryResult, varSymbol.symbol).also {
                    if (it.value in suspendableNodes) suspendableNodes += it
                }
            } else {
                aTry.tryResult
            }

        tryResult.acceptVoid(this)

        if (!isBlockEnded()) {
            setupExceptionState(enclosingCatch)
            doDispatch(exitState)
        }
        addExceptionEdge()

        tryStateMap.remove(aTry)
        tryLoopStack.pop().also { assert(it === aTry) }
        catchBlockStack.pop()

        updateState(tryState.catchState)
        setupExceptionState(enclosingCatch)

        var rethrowNeeded = true

        for (catch in aTry.catches) {
            val type = catch.catchParameter.type
            val initializer = if (type !is IrDynamicType) implicitCast(pendingException(), type) else pendingException()
            val irVar = catch.catchParameter.also { it.initializer = initializer }
            val catchResult =
                if (varSymbol != null && !catch.result.type.isNothing()) {
                    wrap(catch.result, varSymbol.symbol).also {
                        if (it.value in suspendableNodes) suspendableNodes += it
                    }
                } else {
                    catch.result
                }

            if (type is IrDynamicType) {
                rethrowNeeded = false
                addStatement(irVar)
                catchResult.acceptVoid(this)
                maybeDoDispatch(exitState)
            } else {
                val check = buildIsCheck(pendingException(), type)
                val branchBlock = IrCompositeImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, catchResult.type)
                val elseBlock = IrCompositeImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, catchResult.type)
                val irIf = buildIfElse(catchResult.type, check, branchBlock, elseBlock)
                val ifBlock = currentBlock

                currentBlock = branchBlock
                addStatement(irVar)
                catchResult.acceptVoid(this)
                maybeDoDispatch(exitState)

                currentBlock = ifBlock
                addStatement(irIf)
                currentBlock = elseBlock
            }
        }

        if (rethrowNeeded) {
            addExceptionEdge()
            addStatement(IrThrowImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, nothing, pendingException()))
        }

        currentState.successors += enclosingCatch
        if (exitState.hasPredecessors) {
            updateState(exitState)
            setupExceptionState(enclosingCatch)

            if (varSymbol != null) {
                addStatement(IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, varSymbol.type, varSymbol.symbol))
            }
        }
    }

    private fun setupExceptionState(target: SuspendState) {
        target.hasPredecessors = true
        val lastCall = currentBlock.statements.lastOrNull() as? IrCall
        if (lastCall != null && lastCall.symbol == exStateSymbolSetter.symbol) {
            lastCall.arguments[1] = createDispatchPoint(target)
            return
        }
        addStatement(
            buildCall(exStateSymbolSetter.symbol, unit).apply {
                arguments[0] = thisReceiver
                arguments[1] = createDispatchPoint(target)
            },
        )
    }

    private fun exceptionState() = buildCall(exStateSymbolGetter.symbol).apply { dispatchReceiver = thisReceiver }

    private fun pendingException() = buildCall(exceptionSymbolGetter.symbol).apply { dispatchReceiver = thisReceiver }

    private fun buildTryState() = TryState(currentState, SuspendState(unit))

    private fun buildIsCheck(
        value: IrExpression,
        toType: IrType,
    ) = IrTypeOperatorCallImpl(
        UNDEFINED_OFFSET,
        UNDEFINED_OFFSET,
        context.irBuiltIns.booleanType,
        IrTypeOperator.INSTANCEOF,
        toType,
        value,
    )

    private fun buildCall(
        symbol: IrSimpleFunctionSymbol,
        type: IrType = symbol.owner.returnType,
    ) = IrCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type, symbol, typeArgumentsCount = 0)

    private fun buildIfElse(
        type: IrType,
        cond: IrExpression,
        thenBlock: IrExpression,
        elseBlock: IrExpression? = null,
    ): IrWhen =
        IrWhenImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, type).apply {
            branches += IrBranchImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, cond, thenBlock)
            if (elseBlock != null) {
                val constTrue =
                    org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl.boolean(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        context.irBuiltIns.booleanType,
                        true,
                    )
                branches += IrElseBranchImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, constTrue, elseBlock)
            }
        }

    private fun buildVar(
        type: IrType,
        name: String,
        origin: IrDeclarationOrigin = IrDeclarationOrigin.DEFINED,
    ): IrVariable =
        IrVariableImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            origin = origin,
            symbol = IrVariableSymbolImpl(),
            name = Name.identifier(name),
            type = type,
            isVar = true,
            isConst = false,
            isLateinit = false,
        ).apply {
            parent = function.owner
        }

    private fun tempVar(
        type: IrType,
        name: String = "tmp",
    ) = buildVar(type, name, IrDeclarationOrigin.IR_TEMPORARY_VARIABLE).also(::registerLocal)

    private fun registerLocal(variable: IrVariable) {
        allTheIntermediateLocals.add(variable)
    }

    private fun isPureUnusedExpression(stmt: IrStatement): Boolean =
        when (stmt) {
            is IrGetValue,
            is IrConst,
            is IrGetObjectValue,
            -> true

            is IrTypeOperatorCall -> isPureUnusedExpression(stmt.argument)

            else -> false
        }

    private fun extractForwardTarget(state: SuspendState): SuspendState? {
        if (state === rootExceptionTrap) return null
        val stmts = state.entryBlock.statements
        if (stmts.size < 2) return null
        for (i in 0 until stmts.size - 2) {
            if (!isPureUnusedExpression(stmts[i])) return null
        }
        val setStateCall = stmts[stmts.size - 2] as? IrCall ?: return null
        if (setStateCall.symbol != stateSymbolSetter.symbol) return null
        val cont = stmts[stmts.size - 1] as? IrContinue ?: return null
        if (cont.loop !== rootLoop) return null
        val dispatchComposite = setStateCall.arguments[1] as? IrComposite ?: return null
        val target = dispatchComposite.suspendState ?: return null
        return target.takeIf { it !== state }
    }

    private fun collectReachableStates(): List<SuspendState> {
        val visited = linkedSetOf<SuspendState>()
        val queue = ArrayDeque<SuspendState>()
        visited += entryState
        queue += entryState
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (succ in cur.successors) {
                if (visited.add(succ)) {
                    queue += succ
                }
            }
        }
        return visited.toList()
    }

    private fun rewriteDispatchTargets(
        states: List<SuspendState>,
        resolve: (SuspendState) -> SuspendState,
    ) {
        val transformer =
            object : IrElementTransformerVoid() {
                override fun visitComposite(expression: IrComposite): IrExpression {
                    expression.suspendState?.let { target ->
                        expression.suspendState = resolve(target)
                        return expression
                    }
                    return super.visitComposite(expression)
                }
            }

        for (state in states) {
            state.entryBlock.transformChildrenVoid(transformer)
            val updatedSuccessors = state.successors.mapTo(linkedSetOf(), resolve)
            state.successors.clear()
            state.successors.addAll(updatedSuccessors)
        }
        if (hasTryCatch) {
            globalCatch.transformChildrenVoid(transformer)
        }
    }

    private fun simplifyStates() {
        val reachable = collectReachableStates()
        val redirect = hashMapOf<SuspendState, SuspendState>()
        for (state in reachable) {
            if (state !== entryState) {
                extractForwardTarget(state)?.let { target ->
                    redirect[state] = target
                }
            }
        }

        if (redirect.isNotEmpty()) {
            fun resolve(state: SuspendState): SuspendState {
                var cur = state
                val seen = hashSetOf(cur)
                while (true) {
                    val next = redirect[cur] ?: return cur
                    if (!seen.add(next)) return cur
                    cur = next
                }
            }
            rewriteDispatchTargets(reachable, ::resolve)
        }

        val entryTarget = extractForwardTarget(entryState)
        if (entryTarget != null && entryTarget !== entryState && entryTarget !== rootExceptionTrap) {
            entryState.entryBlock.statements.clear()
            entryState.entryBlock.statements.addAll(entryTarget.entryBlock.statements)
            entryState.successors.clear()
            entryState.successors.addAll(entryTarget.successors)

            val afterEntryMerge = collectReachableStates()
            rewriteDispatchTargets(afterEntryMerge) { state ->
                if (state === entryTarget) entryState else state
            }
        }
    }
}
