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
import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.descriptors.synthesizedName
import org.jetbrains.kotlin.backend.common.lower.AbstractSuspendFunctionsLowering
import org.jetbrains.kotlin.backend.common.lower.FinallyBlocksLowering
import org.jetbrains.kotlin.backend.common.lower.ReturnableBlockTransformer
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.optimizations.LivenessAnalysis
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBlockBodyBuilder
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDoWhileLoopImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTryImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.isSuspend
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.DFS

class LuaSuspendFunctionsLowering(
    ctx: LuaIrBackendContext,
) : AbstractSuspendFunctionsLowering<LuaIrBackendContext>(ctx),
    BodyLoweringPass {
    private val coroutineImplExceptionPropertyGetter = ctx.symbols.coroutineImplExceptionPropertyGetter.owner
    private val coroutineImplExceptionPropertySetter = ctx.symbols.coroutineImplExceptionPropertySetter.owner
    private val coroutineImplExceptionStatePropertyGetter = ctx.symbols.coroutineImplExceptionStatePropertyGetter.owner
    private val coroutineImplExceptionStatePropertySetter = ctx.symbols.coroutineImplExceptionStatePropertySetter.owner
    private val coroutineImplLabelPropertySetter = ctx.symbols.coroutineImplLabelPropertySetter.owner
    private val coroutineImplLabelPropertyGetter = ctx.symbols.coroutineImplLabelPropertyGetter.owner
    private val coroutineImplResultSymbolGetter = ctx.symbols.coroutineImplResultSymbolGetter.owner

    override val stateMachineMethodName = Name.identifier("doResume")

    override fun getCoroutineBaseClass(function: IrFunction) = context.symbols.coroutineImpl

    override fun nameForCoroutineClass(function: IrFunction) = "${function.name}COROUTINE\$".synthesizedName

    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        val enclosingContainer = findEnclosingDeclarationContainer(container)

        irBody.acceptChildrenVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitFunctionExpression(expression: IrFunctionExpression) {
                    super.visitFunctionExpression(expression)
                    val lambdaFn = expression.function
                    val lambdaBody = lambdaFn.body ?: return
                    if (!lambdaFn.isSuspend) return

                    val capturedValues = collectCapturedValues(lambdaFn, lambdaBody)
                    transformSuspendFunction(lambdaFn, lambdaBody)?.let { coroutineClass ->
                        enclosingContainer.addChild(coroutineClass)
                        if (capturedValues.isNotEmpty()) {
                            wireCapturedValues(lambdaFn, coroutineClass, capturedValues)
                        }
                    }
                }
            },
        )

        if (container is IrSimpleFunction && container.isSuspend) {
            transformSuspendFunction(container, irBody)?.let {
                enclosingContainer.addChild(it)
            }
        }
    }

    private fun findEnclosingDeclarationContainer(declaration: IrDeclaration): IrDeclarationContainer {
        var current: IrElement? = declaration.parent
        while (current != null) {
            if (current is IrDeclarationContainer) return current
            current = (current as? IrDeclaration)?.parent
        }
        error("No enclosing IrDeclarationContainer for $declaration")
    }

    private fun collectCapturedValues(
        fn: IrFunction,
        body: IrBody,
    ): List<IrValueDeclaration> {
        val localValues = hashSetOf<IrValueDeclaration>().apply { addAll(fn.parameters) }
        body.acceptVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitVariable(declaration: IrVariable) {
                    localValues += declaration
                    super.visitVariable(declaration)
                }

                override fun visitValueParameter(declaration: IrValueParameter) {
                    localValues += declaration
                    super.visitValueParameter(declaration)
                }
            },
        )

        val captured = linkedSetOf<IrValueDeclaration>()
        body.acceptVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitGetValue(expression: IrGetValue) {
                    val owner = expression.symbol.owner
                    if (owner !in localValues) {
                        captured += owner
                    }
                    super.visitGetValue(expression)
                }
            },
        )
        return captured.toList()
    }

    private fun wireCapturedValues(
        lambdaFn: IrFunction,
        coroutineClass: IrClass,
        capturedValues: List<IrValueDeclaration>,
    ) {
        val ctor = coroutineClass.primaryConstructor ?: return
        val doResume = coroutineClass.declarations.filterIsInstance<IrSimpleFunction>().first { it.name == stateMachineMethodName }
        val ctorThis = ctor.dispatchReceiverParameter ?: coroutineClass.thisReceiver!!
        val doResumeThis = doResume.dispatchReceiverParameter!!

        val capturedToField = linkedMapOf<IrValueSymbol, IrField>()
        val ctorBody = ctor.body as? IrBlockBody ?: return

        capturedValues.forEachIndexed { idx, captured ->
            val field =
                coroutineClass.addField {
                    name = Name.identifier("cap\$$idx")
                    type = captured.type
                    visibility = DescriptorVisibilities.PRIVATE
                    isFinal = true
                }
            val param =
                ctor.addValueParameter {
                    name = Name.identifier("cap\$$idx")
                    type = captured.type
                }
            ctorBody.statements.add(
                IrSetFieldImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    field.symbol,
                    IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, ctorThis.type, ctorThis.symbol),
                    IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, param.type, param.symbol),
                    context.irBuiltIns.unitType,
                ),
            )
            capturedToField[captured.symbol] = field
        }

        lambdaFn.body?.transformChildrenVoid(
            object : IrElementTransformerVoid() {
                override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
                    if (expression.symbol == ctor.symbol) {
                        capturedValues.forEach { captured ->
                            expression.arguments.add(
                                IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, captured.type, captured.symbol),
                            )
                        }
                    }
                    return super.visitConstructorCall(expression)
                }
            },
        )

        doResume.body?.transformChildrenVoid(
            object : IrElementTransformerVoid() {
                override fun visitGetValue(expression: IrGetValue): IrExpression {
                    val field = capturedToField[expression.symbol] ?: return super.visitGetValue(expression)
                    return IrGetFieldImpl(
                        expression.startOffset,
                        expression.endOffset,
                        field.symbol,
                        expression.type,
                        IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, doResumeThis.type, doResumeThis.symbol),
                        expression.origin,
                    )
                }
            },
        )
    }

    private fun transformSuspendFunction(
        function: IrSimpleFunction,
        body: IrBody,
    ): IrClass? {
        assert(function.isSuspend)

        return when (val functionKind = getSuspendFunctionKind(context, function, body)) {
            is SuspendFunctionKind.NoSuspendCalls -> {
                null
            }

            is SuspendFunctionKind.Delegating -> {
                removeReturnIfSuspendedCallAndSimplifyDelegatingCall(function, functionKind.delegatingCall)
                null
            }

            is SuspendFunctionKind.NeedsStateMachine -> {
                buildCoroutine(function, isSuspendLambdaInvokeMethod = false)
            }
        }
    }

    private fun removeReturnIfSuspendedCallAndSimplifyDelegatingCall(
        irFunction: IrFunction,
        delegatingCall: IrCall,
    ) {
        val returnValue =
            if (delegatingCall.symbol == context.symbols.returnIfSuspended) {
                delegatingCall.arguments[0]!!
            } else {
                delegatingCall
            }

        val body = irFunction.body as IrBlockBody
        val statements = body.statements
        val lastStatement = statements.last()

        context
            .createIrBuilder(
                irFunction.symbol,
                startOffset = lastStatement.startOffset,
                endOffset = lastStatement.endOffset,
            ).run {
                assert(lastStatement == delegatingCall || lastStatement is IrReturn) { "Unexpected statement $lastStatement" }
                val tempVar =
                    scope.createTemporaryVariable(
                        irExpression = returnValue,
                        irType = context.irBuiltIns.anyType,
                    )
                statements[statements.lastIndex] = tempVar
                statements.add(irReturn(irGet(tempVar)))
            }
    }

    override fun buildStateMachine(
        stateMachineFunction: IrFunction,
        transformingFunction: IrFunction,
        argumentToPropertiesMap: Map<IrValueParameter, IrField>,
    ) {
        val returnableBlockTransformer = ReturnableBlockTransformer(context)
        val finallyBlockTransformer = FinallyBlocksLowering(context, context.irBuiltIns.throwableType)
        val simplifiedFunction =
            transformingFunction
                .transform(finallyBlockTransformer, null)
                .transform(returnableBlockTransformer, null) as IrFunction

        val originalBody = simplifiedFunction.body as IrBlockBody

        val body =
            IrBlockImpl(
                simplifiedFunction.startOffset,
                simplifiedFunction.endOffset,
                context.irBuiltIns.unitType,
                null,
                originalBody.statements,
            )

        val coroutineClass = stateMachineFunction.parent as IrClass
        val thisReceiver = stateMachineFunction.dispatchReceiverParameter!!.symbol
        val suspendResult =
            IrVariableImpl(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                origin = org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.DEFINED,
                symbol = IrVariableSymbolImpl(),
                name = Name.identifier("suspendResult"),
                type = context.irBuiltIns.anyNType,
                isVar = true,
                isConst = false,
                isLateinit = false,
            ).apply {
                parent = stateMachineFunction
                initializer =
                    IrCallImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        coroutineImplResultSymbolGetter.returnType,
                        coroutineImplResultSymbolGetter.symbol,
                        typeArgumentsCount = 0,
                    ).apply {
                        dispatchReceiver = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, thisReceiver.owner.type, thisReceiver)
                    }
            }

        val unit = context.irBuiltIns.unitType
        val switch = IrWhenImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unit)
        val stateVar =
            IrVariableImpl(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                origin = org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin.DEFINED,
                symbol = IrVariableSymbolImpl(),
                name = Name.identifier("stateVar"),
                type = context.irBuiltIns.intType,
                isVar = false,
                isConst = false,
                isLateinit = false,
            ).apply {
                parent = stateMachineFunction
                initializer =
                    IrCallImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        coroutineImplLabelPropertyGetter.returnType,
                        coroutineImplLabelPropertyGetter.symbol,
                        typeArgumentsCount = 0,
                    ).apply {
                        dispatchReceiver = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, thisReceiver.owner.type, thisReceiver)
                    }
            }

        val switchBlock =
            IrBlockImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unit).apply {
                statements += stateVar
                statements += switch
            }
        val rootTry = IrTryImpl(body.startOffset, body.endOffset, unit).apply { tryResult = switchBlock }
        val rootLoop =
            IrDoWhileLoopImpl(
                body.startOffset,
                body.endOffset,
                unit,
                null,
            ).also {
                it.condition = IrConstImpl.boolean(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.booleanType, true)
                it.body = switchBlock
                it.label = "\$sm"
            }

        val suspendableNodes = collectSuspendableNodes(body)

        val stateMachineBuilder =
            StateMachineBuilder(
                suspendableNodes = suspendableNodes,
                context = context,
                function = stateMachineFunction.symbol,
                rootLoop = rootLoop,
                exceptionSymbolGetter = coroutineImplExceptionPropertyGetter,
                exceptionSymbolSetter = coroutineImplExceptionPropertySetter,
                exStateSymbolGetter = coroutineImplExceptionStatePropertyGetter,
                exStateSymbolSetter = coroutineImplExceptionStatePropertySetter,
                stateSymbolSetter = coroutineImplLabelPropertySetter,
                thisSymbol = thisReceiver,
                getSuspendResultAsType = { type ->
                    IrTypeOperatorCallImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        type,
                        IrTypeOperator.IMPLICIT_CAST,
                        type,
                        IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, suspendResult.type, suspendResult.symbol),
                    )
                },
                setSuspendResultValue = { value ->
                    IrSetValueImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        unit,
                        suspendResult.symbol,
                        IrTypeOperatorCallImpl(
                            UNDEFINED_OFFSET,
                            UNDEFINED_OFFSET,
                            context.irBuiltIns.anyNType,
                            IrTypeOperator.IMPLICIT_CAST,
                            context.irBuiltIns.anyNType,
                            value,
                        ),
                        null,
                    )
                },
            )

        body.acceptVoid(stateMachineBuilder)
        stateMachineBuilder.finalizeStateMachine()

        if (stateMachineBuilder.hasTryCatch) {
            val rootTry =
                IrTryImpl(body.startOffset, body.endOffset, unit).apply {
                    tryResult = switchBlock
                    catches += stateMachineBuilder.globalCatch
                }
            rootLoop.body = rootTry
        }

        assignStateIds(stateMachineBuilder.entryState, stateVar.symbol, switch, rootLoop)

        val initExceptionStateStmt =
            if (stateMachineBuilder.hasTryCatch) {
                val receiver1 = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, thisReceiver.owner.type, thisReceiver)
                val receiver2 = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, thisReceiver.owner.type, thisReceiver)
                val exceptionTrapId = stateMachineBuilder.rootExceptionTrap.id
                check(exceptionTrapId >= 0)
                val getExState =
                    IrCallImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        coroutineImplExceptionStatePropertyGetter.returnType,
                        coroutineImplExceptionStatePropertyGetter.symbol,
                        typeArgumentsCount = 0,
                    ).apply {
                        dispatchReceiver = receiver1
                    }
                val isUnset =
                    IrCallImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        context.irBuiltIns.booleanType,
                        context.irBuiltIns.lessFunByOperandType[context.irBuiltIns.intClass]!!,
                        typeArgumentsCount = 0,
                    ).apply {
                        arguments[0] = getExState
                        arguments[1] = IrConstImpl.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.intType, 0)
                    }
                val setTrap =
                    IrCallImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        unit,
                        coroutineImplExceptionStatePropertySetter.symbol,
                        typeArgumentsCount = 0,
                    ).apply {
                        arguments[0] = receiver2
                        arguments[1] = IrConstImpl.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.intType, exceptionTrapId)
                    }
                IrWhenImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unit).apply {
                    branches += IrBranchImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, isUnset, setTrap)
                }
            } else {
                null
            }

        val functionBody =
            context.irFactory.createBlockBody(
                stateMachineFunction.startOffset,
                stateMachineFunction.endOffset,
                stateMachineBuilder.allTheIntermediateLocals + suspendResult + listOfNotNull(initExceptionStateStmt) + rootLoop,
            )

        stateMachineFunction.body = functionBody

        functionBody.transformChildrenVoid(
            object : IrElementTransformerVoid() {
                override fun visitReturn(expression: IrReturn): IrExpression {
                    expression.transformChildrenVoid(this)
                    return if (expression.returnTargetSymbol != simplifiedFunction.symbol) {
                        expression
                    } else {
                        IrReturnImpl(
                            expression.startOffset,
                            expression.endOffset,
                            context.irBuiltIns.nothingType,
                            stateMachineFunction.symbol,
                            expression.value,
                        )
                    }
                }
            },
        )

        val liveLocals =
            withFunctionExpressionsHidden(functionBody) {
                LivenessAnalysis
                    .run(functionBody, { it is IrCall && it.isSuspend })
                    .values
                    .flatten()
                    .toSet()
            }

        val localToPropertyMap = hashMapOf<IrValueSymbol, IrFieldSymbol>()
        var localCounter = 0
        liveLocals.forEach { liveLocal ->
            if (liveLocal !== suspendResult &&
                liveLocal !== stateVar &&
                (liveLocal.parent === transformingFunction || liveLocal.parent === stateMachineFunction)
            ) {
                localToPropertyMap.computeIfAbsent(liveLocal.symbol) {
                    coroutineClass
                        .addField(Name.identifier("${liveLocal.name}${localCounter++}"), liveLocal.type, liveLocal.isVar)
                        .symbol
                }
            }
        }
        val isSuspendLambda = transformingFunction.parent === coroutineClass
        val parameters = if (isSuspendLambda) simplifiedFunction.nonDispatchParameters else simplifiedFunction.parameters
        for (parameter in parameters) {
            localToPropertyMap.computeIfAbsent(parameter.symbol) {
                argumentToPropertiesMap.getValue(parameter).symbol
            }
        }

        stateMachineFunction.body!!.patchDeclarationParents(stateMachineFunction)
        stateMachineFunction.transform(
            LiveLocalsTransformer(
                localToPropertyMap,
                { IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, thisReceiver.owner.type, thisReceiver) },
                unit,
            ),
            null,
        )
    }

    private fun <T> withFunctionExpressionsHidden(
        body: IrBlockBody,
        block: () -> T,
    ): T {
        val replacements = linkedMapOf<IrBlockImpl, IrFunctionExpression>()

        fun collectReferencedValues(fn: IrFunction): List<IrGetValue> {
            val locals = hashSetOf<IrValueDeclaration>().apply { addAll(fn.parameters) }
            val gets = mutableListOf<IrGetValue>()
            fn.body?.acceptVoid(
                object : IrVisitorVoid() {
                    override fun visitElement(element: IrElement) {
                        element.acceptChildrenVoid(this)
                    }

                    override fun visitVariable(declaration: IrVariable) {
                        locals += declaration
                        super.visitVariable(declaration)
                    }

                    override fun visitValueParameter(declaration: IrValueParameter) {
                        locals += declaration
                        super.visitValueParameter(declaration)
                    }

                    override fun visitGetValue(expression: IrGetValue) {
                        if (expression.symbol.owner !in locals) {
                            gets +=
                                IrGetValueImpl(
                                    expression.startOffset,
                                    expression.endOffset,
                                    expression.type,
                                    expression.symbol,
                                    expression.origin,
                                )
                        }
                        super.visitGetValue(expression)
                    }
                },
            )
            return gets
        }

        body.transformChildrenVoid(
            object : IrElementTransformerVoid() {
                override fun visitFunctionExpression(expression: IrFunctionExpression): IrExpression {
                    val reads = collectReferencedValues(expression.function)
                    val statements =
                        if (reads.isEmpty()) {
                            emptyList()
                        } else {
                            reads.dropLast(1) +
                                IrTypeOperatorCallImpl(
                                    UNDEFINED_OFFSET,
                                    UNDEFINED_OFFSET,
                                    expression.type,
                                    IrTypeOperator.REINTERPRET_CAST,
                                    expression.type,
                                    reads.last(),
                                )
                        }
                    val placeholder =
                        IrBlockImpl(
                            expression.startOffset,
                            expression.endOffset,
                            expression.type,
                            expression.origin,
                            statements,
                        )
                    replacements[placeholder] = expression
                    return placeholder
                }
            },
        )

        try {
            return block()
        } finally {
            if (replacements.isNotEmpty()) {
                body.transformChildrenVoid(
                    object : IrElementTransformerVoid() {
                        override fun visitBlock(expression: IrBlock): IrExpression =
                            replacements[expression] ?: super.visitBlock(expression)
                    },
                )
            }
        }
    }

    private fun assignStateIds(
        entryState: SuspendState,
        subject: IrVariableSymbol,
        switch: IrWhen,
        rootLoop: IrLoop,
    ) {
        val visited = mutableSetOf<SuspendState>()
        val sortedStates = DFS.topologicalOrder(listOf(entryState), { it.successors }, { visited.add(it) })
        sortedStates.withIndex().forEach { it.value.id = it.index }

        val eqeqeqInt = context.irBuiltIns.eqeqeqSymbol

        for (state in sortedStates) {
            val condition =
                IrCallImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    context.irBuiltIns.booleanType,
                    eqeqeqInt,
                    typeArgumentsCount = 0,
                ).apply {
                    arguments[0] = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, subject.owner.type, subject)
                    arguments[1] = IrConstImpl.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.intType, state.id)
                }
            switch.branches += IrBranchImpl(state.entryBlock.startOffset, state.entryBlock.endOffset, condition, state.entryBlock)
        }

        val dispatchPointTransformer =
            DispatchPointTransformer {
                assert(it.id >= 0)
                IrConstImpl.int(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.intType, it.id)
            }
        rootLoop.transformChildrenVoid(dispatchPointTransformer)
    }

    override fun IrBlockBodyBuilder.generateCoroutineStart(
        invokeSuspendFunction: IrFunction,
        receiver: IrExpression,
    ) = +irReturn(irCall(invokeSuspendFunction.symbol).apply { arguments[0] = receiver })
}

internal sealed class SuspendFunctionKind {
    object NoSuspendCalls : SuspendFunctionKind()

    class Delegating(
        val delegatingCall: IrCall,
    ) : SuspendFunctionKind()

    object NeedsStateMachine : SuspendFunctionKind()
}

internal fun getSuspendFunctionKind(
    context: CommonBackendContext,
    function: IrSimpleFunction,
    body: IrBody,
): SuspendFunctionKind {
    var numberOfSuspendCalls = 0
    body.acceptVoid(
        object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitFunction(declaration: IrFunction) {
            }

            override fun visitCall(expression: IrCall) {
                expression.acceptChildrenVoid(this)
                if (expression.isSuspend) {
                    ++numberOfSuspendCalls
                }
            }
        },
    )

    val lastCall =
        when (val lastStatement = (body as IrBlockBody).statements.lastOrNull()) {
            is IrCall -> {
                if (lastStatement.type == context.irBuiltIns.unitType && function.returnType == context.irBuiltIns.unitType) {
                    lastStatement
                } else {
                    null
                }
            }

            is IrReturn -> {
                fun IrTypeOperatorCall.isImplicitCast(): Boolean =
                    operator == IrTypeOperator.IMPLICIT_CAST || operator == IrTypeOperator.IMPLICIT_COERCION_TO_UNIT

                var value: IrElement = lastStatement
                loop@ while (true) {
                    value =
                        when {
                            value is IrBlock && value.statements.size == 1 -> value.statements.first()
                            value is IrReturn -> value.value
                            value is IrTypeOperatorCall && value.isImplicitCast() -> value.argument
                            else -> break@loop
                        }
                }
                value as? IrCall
            }

            else -> {
                null
            }
        }
    val suspendCallAtEnd = lastCall != null && lastCall.isSuspend
    return when (numberOfSuspendCalls) {
        0 -> SuspendFunctionKind.NoSuspendCalls
        1 if suspendCallAtEnd -> SuspendFunctionKind.Delegating(lastCall)
        else -> SuspendFunctionKind.NeedsStateMachine
    }
}
