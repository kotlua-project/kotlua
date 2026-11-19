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
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.transformStatement
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.isElseBranch
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.util.transformFlat
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.assignFrom
import org.jetbrains.kotlin.utils.memoryOptimizedMap

class LuaBlockDecomposerLowering(
    private val context: LuaIrBackendContext,
) : BodyLoweringPass {
    private val nothingType = context.irBuiltIns.nothingType

    private fun unreachableExpression(): IrExpression =
        IrCallImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = nothingType,
            symbol = context.symbols.unreachable,
            typeArgumentsCount = 0,
        )

    private val decomposerTransformer = LuaBlockDecomposerTransformer(context, ::unreachableExpression)

    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        when (container) {
            is IrFunction -> {
                container.accept(decomposerTransformer, null)
                irBody.patchDeclarationParents(container)
            }

            is IrField -> {
                container.initializer?.apply {
                    val initFunction =
                        context.irFactory
                            .buildFun {
                                name = Name.identifier(container.name.asString() + $$"$init$")
                                returnType = container.type
                                visibility = DescriptorVisibilities.PRIVATE
                                origin = SYNTHESIZED_DECLARATION
                            }.apply {
                                parent = container.parent
                            }

                    val newBody = toBlockBody(initFunction)
                    newBody.patchDeclarationParents(initFunction)
                    initFunction.body = newBody

                    initFunction.accept(decomposerTransformer, null)

                    val lastStatement = newBody.statements.last()
                    val actualParent =
                        if (newBody.statements.size > 1 || lastStatement !is IrReturn || lastStatement.value != expression) {
                            expression =
                                IrCallImpl(
                                    startOffset = UNDEFINED_OFFSET,
                                    endOffset = UNDEFINED_OFFSET,
                                    type = expression.type,
                                    symbol = initFunction.symbol,
                                    typeArgumentsCount = 0,
                                )
                            (container.parent as IrDeclarationContainer).declarations += initFunction
                            initFunction
                        } else {
                            container
                        }

                    patchDeclarationParents(actualParent)
                }
            }
        }
    }

    private fun IrExpressionBody.toBlockBody(containingFunction: IrFunction): IrBlockBody =
        context.irFactory.createBlockBody(startOffset, endOffset) {
            expression.patchDeclarationParents(containingFunction)
            statements +=
                IrReturnImpl(
                    startOffset = UNDEFINED_OFFSET,
                    endOffset = UNDEFINED_OFFSET,
                    type = nothingType,
                    returnTargetSymbol = containingFunction.symbol,
                    value = expression,
                )
        }

    companion object {
        private val SYNTHESIZED_DECLARATION = IrDeclarationOriginImpl("SYNTHESIZED_DECLARATION")
    }
}

private class LuaBlockDecomposerTransformer(
    private val context: LuaIrBackendContext,
    private val unreachableExpression: () -> IrExpression,
) : IrElementTransformerVoid() {
    private lateinit var function: IrDeclarationParent
    private var tmpVarCounter: Int = 0

    private val statementTransformer = StatementTransformer()
    private val expressionTransformer = ExpressionTransformer()

    private val constTrue
        get() = IrConstImpl.boolean(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.booleanType, true)

    private val constFalse
        get() = IrConstImpl.boolean(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.booleanType, false)

    private val unitType = context.irBuiltIns.unitType
    private val unitValue
        get() = IrGetObjectValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unitType, context.irBuiltIns.unitClass)

    private val booleanNotSymbol = context.irBuiltIns.booleanNotSymbol

    override fun visitScript(declaration: IrScript): IrStatement {
        function = declaration
        with(declaration) {
            val transformedStatements = mutableListOf<IrStatement>()
            statements.forEach {
                val transformer = if (it === statements.last()) expressionTransformer else statementTransformer
                val s = it.transformStatement(transformer)
                if (s is IrComposite) {
                    transformedStatements.addAll(s.statements)
                } else {
                    transformedStatements.add(s)
                }
            }
            statements.clear()
            statements.addAll(transformedStatements)
        }
        return declaration
    }

    override fun visitFunction(declaration: IrFunction): IrStatement {
        function = declaration
        tmpVarCounter = 0
        return declaration.transformStatement(statementTransformer)
    }

    override fun visitElement(element: IrElement) = element.transform(statementTransformer, null)

    private fun processStatements(statements: MutableList<IrStatement>) {
        statements.transformFlat {
            destructureComposite(it.transformStatement(statementTransformer))
        }
    }

    private fun makeTempVar(
        type: IrType,
        init: IrExpression? = null,
    ): IrVariable =
        IrVariableImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            symbol = IrVariableSymbolImpl(),
            name = Name.identifier("tmp\$${tmpVarCounter++}"),
            type = type,
            isVar = true,
            isConst = false,
            isLateinit = false,
        ).apply {
            parent = function
            initializer = init
        }

    private fun makeLoopLabel() = "\$l\$${tmpVarCounter++}"

    private fun buildComposite(
        type: IrType,
        statements: List<IrStatement> = emptyList(),
    ): IrCompositeImpl =
        IrCompositeImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = type,
            origin = null,
            statements = statements,
        )

    private fun buildGetValue(variable: IrVariable): IrGetValueImpl =
        IrGetValueImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = variable.type,
            symbol = variable.symbol,
        )

    private fun buildSetVariable(
        variable: IrVariable,
        value: IrExpression,
    ): IrSetValueImpl =
        IrSetValueImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = unitType,
            symbol = variable.symbol,
            value = value,
            origin = null,
        )

    private fun buildIfElse(
        startOffset: Int = UNDEFINED_OFFSET,
        endOffset: Int = UNDEFINED_OFFSET,
        type: IrType,
        cond: IrExpression,
        thenBranch: IrExpression,
        elseBranch: IrExpression? = null,
        origin: IrStatementOrigin? = null,
        thenBranchStartOffset: Int = UNDEFINED_OFFSET,
        thenBranchEndOffset: Int = UNDEFINED_OFFSET,
        elseBranchStartOffset: Int = UNDEFINED_OFFSET,
        elseBranchEndOffset: Int = UNDEFINED_OFFSET,
    ): IrWhenImpl =
        IrWhenImpl(startOffset, endOffset, type, origin).apply {
            branches += IrBranchImpl(thenBranchStartOffset, thenBranchEndOffset, cond, thenBranch)
            if (elseBranch != null) {
                branches += IrElseBranchImpl(elseBranchStartOffset, elseBranchEndOffset, constTrue, elseBranch)
            }
        }

    private fun IrStatement.asExpression(last: IrExpression): IrExpression {
        val composite = buildComposite(last.type)
        composite.statements += transformStatement(statementTransformer)
        composite.statements += last
        return composite
    }

    private fun materializeLastExpression(
        composite: IrComposite,
        block: (IrExpression) -> IrStatement,
    ): IrComposite {
        val statements = composite.statements
        val expression = statements.lastOrNull() as? IrExpression ?: return composite
        statements[statements.lastIndex] = block(expression)
        return composite
    }

    private fun destructureComposite(expression: IrStatement): List<IrStatement> =
        (expression as? IrComposite)?.statements ?: listOf(expression)

    private inner class BreakContinueUpdater(
        val breakLoop: IrLoop,
        val continueLoop: IrLoop,
    ) : IrTransformer<IrLoop>() {
        override fun visitBreak(
            jump: IrBreak,
            data: IrLoop,
        ) = jump.apply {
            if (loop == data) loop = breakLoop
        }

        override fun visitContinue(
            jump: IrContinue,
            data: IrLoop,
        ) = jump.apply {
            if (loop == data) loop = continueLoop
        }
    }

    private inner class StatementTransformer : IrElementTransformerVoid() {
        override fun visitBlockBody(body: IrBlockBody) = body.apply { processStatements(statements) }

        override fun visitContainerExpression(expression: IrContainerExpression) = expression.apply { processStatements(statements) }

        override fun visitExpression(expression: IrExpression) = expression.transform(expressionTransformer, null)

        override fun visitFunction(declaration: IrFunction): IrStatement {
            if (declaration !== function) {
                LuaBlockDecomposerTransformer(context, unreachableExpression).visitFunction(declaration)
                return declaration
            }
            return super.visitFunction(declaration)
        }

        override fun visitReturn(expression: IrReturn): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val composite = expression.value as? IrComposite ?: return expression
            return materializeLastExpression(composite) {
                IrReturnImpl(expression.startOffset, expression.endOffset, expression.type, expression.returnTargetSymbol, it)
            }
        }

        override fun visitThrow(expression: IrThrow): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val composite = expression.value as? IrComposite ?: return expression
            return materializeLastExpression(composite) {
                IrThrowImpl(expression.startOffset, expression.endOffset, expression.type, it)
            }
        }

        override fun visitBreakContinue(jump: IrBreakContinue) = jump

        override fun visitVariable(declaration: IrVariable): IrStatement {
            declaration.transformChildrenVoid(expressionTransformer)

            val composite = declaration.initializer as? IrComposite ?: return declaration
            return materializeLastExpression(composite) {
                declaration.apply { initializer = it }
            }
        }

        override fun visitSetField(expression: IrSetField): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val receiverResult = expression.receiver as? IrComposite
            val valueResult = expression.value as? IrComposite
            if (receiverResult == null && valueResult == null) return expression

            if (receiverResult != null && valueResult != null) {
                val result = IrCompositeImpl(receiverResult.startOffset, expression.endOffset, unitType)
                val receiverValue = receiverResult.statements.last() as IrExpression
                val irVar = makeTempVar(receiverValue.type, receiverValue)
                val setValue = valueResult.statements.last() as IrExpression
                result.statements += receiverResult.statements.subList(0, receiverResult.statements.lastIndex)
                result.statements += irVar
                result.statements += valueResult.statements.subList(0, valueResult.statements.lastIndex)
                result.statements +=
                    expression.run {
                        IrSetFieldImpl(
                            startOffset,
                            endOffset,
                            symbol,
                            buildGetValue(irVar),
                            setValue,
                            type,
                            origin,
                            superQualifierSymbol,
                        )
                    }
                return result
            }

            if (receiverResult != null) {
                return materializeLastExpression(receiverResult) {
                    expression.run {
                        IrSetFieldImpl(startOffset, endOffset, symbol, it, value, type, origin, superQualifierSymbol)
                    }
                }
            }

            assert(valueResult != null)

            val receiver =
                expression.receiver?.let {
                    val irVar = makeTempVar(it.type, it)
                    valueResult!!.statements.add(0, irVar)
                    buildGetValue(irVar)
                }

            return materializeLastExpression(valueResult!!) {
                expression.run {
                    IrSetFieldImpl(startOffset, endOffset, symbol, receiver, it, type, origin, superQualifierSymbol)
                }
            }
        }

        override fun visitSetValue(expression: IrSetValue): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val composite = expression.value as? IrComposite ?: return expression

            return materializeLastExpression(composite) {
                expression.run { IrSetValueImpl(startOffset, endOffset, type, symbol, it, origin) }
            }
        }

        override fun visitWhileLoop(loop: IrWhileLoop): IrExpression {
            val newBody = loop.body?.transform(statementTransformer, null)
            val newCondition = loop.condition.transform(expressionTransformer, null)

            if (newCondition is IrComposite) {
                val newLoopBody = IrBlockImpl(loop.startOffset, loop.endOffset, loop.type, loop.origin)

                newLoopBody.statements += newCondition.statements.subList(0, newCondition.statements.lastIndex)
                val breakExpr = IrBreakImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unitType, loop)
                val thenBlock = IrBlockImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unitType, null, listOf(breakExpr))

                val newLoopCondition = newCondition.statements.last() as IrExpression
                val breakCond =
                    IrCallImpl(
                        startOffset = UNDEFINED_OFFSET,
                        endOffset = UNDEFINED_OFFSET,
                        type = context.irBuiltIns.booleanType,
                        symbol = booleanNotSymbol,
                        typeArgumentsCount = 0,
                    ).apply {
                        arguments[0] = newLoopCondition
                    }

                newLoopBody.statements += buildIfElse(type = unitType, cond = breakCond, thenBranch = thenBlock)
                newLoopBody.statements.addIfNotNull(newBody)

                return loop.apply {
                    condition = constTrue
                    body = newLoopBody
                }
            }

            return loop.apply {
                body = newBody
                condition = newCondition
            }
        }

        override fun visitDoWhileLoop(loop: IrDoWhileLoop): IrExpression {
            val newBody = loop.body?.transform(statementTransformer, null)
            val newCondition = loop.condition.transform(expressionTransformer, null)

            if (newCondition is IrComposite) {
                val innerLoop =
                    IrDoWhileLoopImpl(loop.startOffset, loop.endOffset, unitType, loop.origin).apply {
                        condition = constFalse
                        body = newBody
                        label = makeLoopLabel()
                    }

                val newLoopCondition = newCondition.statements.last() as IrExpression
                val newLoopBody =
                    IrBlockImpl(
                        newBody?.startOffset ?: newCondition.startOffset,
                        newBody?.endOffset ?: newCondition.endOffset,
                        newBody?.type ?: unitType,
                    ).apply {
                        statements += innerLoop
                        statements += newCondition.statements.subList(0, newCondition.statements.lastIndex)
                    }

                val newLoop = IrDoWhileLoopImpl(loop.startOffset, loop.endOffset, unitType, loop.origin)

                return newLoop.apply {
                    condition = newLoopCondition
                    body = newLoopBody.transform(BreakContinueUpdater(newLoop, innerLoop), loop)
                    label = loop.label ?: makeLoopLabel()
                }
            }

            return loop.apply {
                body = newBody
                condition = newCondition
            }
        }

        override fun visitWhen(expression: IrWhen): IrExpression {
            var compositeCount = 0

            val results =
                expression.branches.map {
                    val cond = it.condition.transform(expressionTransformer, null)
                    val res = it.result.transform(statementTransformer, null)
                    if (cond is IrComposite) compositeCount++
                    Triple(cond, res, it)
                }

            if (compositeCount == 0) {
                val branches =
                    results.memoryOptimizedMap { (cond, res, orig) ->
                        when {
                            isElseBranch(orig) -> IrElseBranchImpl(orig.startOffset, orig.endOffset, cond, res)
                            else -> IrBranchImpl(orig.startOffset, orig.endOffset, cond, res)
                        }
                    }
                return expression.run { IrWhenImpl(startOffset, endOffset, type, origin, branches) }
            }

            val block = IrBlockImpl(expression.startOffset, expression.endOffset, unitType, expression.origin)
            var appendBlock: IrBlockImpl = block

            results.forEachIndexed { i, (cond, res, orig) ->
                val condStatements = destructureComposite(cond)
                val condValue = condStatements.last() as IrExpression

                appendBlock.statements += condStatements.subList(0, condStatements.lastIndex)

                appendBlock =
                    IrBlockImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unitType).also { nextBlock ->
                        val elseBlock = nextBlock.takeIf { results.lastIndex != i }
                        val additionalStatements =
                            when {
                                isElseBranch(orig) -> {
                                    (res as? IrBlock)?.statements ?: listOf(res)
                                }

                                else -> {
                                    listOf(
                                        buildIfElse(
                                            type = unitType,
                                            cond = condValue,
                                            thenBranch = res,
                                            elseBranch = elseBlock,
                                            origin = expression.origin,
                                            thenBranchStartOffset = orig.startOffset,
                                            thenBranchEndOffset = orig.endOffset,
                                        ),
                                    )
                                }
                            }

                        appendBlock.statements += additionalStatements
                    }
            }

            return block
        }

        override fun visitTry(aTry: IrTry) = aTry.also { it.transformChildrenVoid(this) }
    }

    private inner class ExpressionTransformer : IrElementTransformerVoid() {
        override fun visitExpression(expression: IrExpression) = expression.apply { transformChildrenVoid() }

        override fun visitGetField(expression: IrGetField): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val composite = expression.receiver as? IrComposite ?: return expression

            return materializeLastExpression(composite) {
                expression.run { IrGetFieldImpl(startOffset, endOffset, symbol, type, it, origin, superQualifierSymbol) }
            }
        }

        override fun visitTypeOperator(expression: IrTypeOperatorCall): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val composite = expression.argument as? IrComposite ?: return expression

            return materializeLastExpression(composite) {
                expression.apply { argument = it }
            }
        }

        override fun visitGetClass(expression: IrGetClass): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val composite = expression.argument as? IrComposite ?: return expression

            return materializeLastExpression(composite) {
                expression.run { IrGetClassImpl(startOffset, endOffset, type, it) }
            }
        }

        override fun visitFunctionExpression(expression: IrFunctionExpression): IrExpression {
            LuaBlockDecomposerTransformer(context, unreachableExpression).visitFunction(expression.function)
            return expression
        }

        override fun visitLoop(loop: IrLoop) = loop.asExpression(unitValue)

        override fun visitSetValue(expression: IrSetValue) = expression.asExpression(unitValue)

        override fun visitSetField(expression: IrSetField) = expression.asExpression(unitValue)

        override fun visitBreakContinue(jump: IrBreakContinue) = jump.asExpression(unreachableExpression())

        override fun visitThrow(expression: IrThrow) = expression.asExpression(unreachableExpression())

        override fun visitReturn(expression: IrReturn) = expression.asExpression(unreachableExpression())

        override fun visitVariable(declaration: IrVariable) = declaration.asExpression(unitValue)

        override fun visitStringConcatenation(expression: IrStringConcatenation): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val compositeCount = expression.arguments.count { it is IrComposite }
            if (compositeCount == 0) return expression

            val newStatements = mutableListOf<IrStatement>()
            val arguments = mapArguments(expression.arguments, compositeCount, newStatements)

            newStatements +=
                expression.run {
                    IrStringConcatenationImpl(startOffset, endOffset, type, arguments.memoryOptimizedMap { it!! })
                }
            return buildComposite(expression.type, newStatements)
        }

        private fun mapArguments(
            oldArguments: Collection<IrExpression?>,
            compositeCount: Int,
            newStatements: MutableList<IrStatement>,
            dontDetachFirstArgument: Boolean = false,
        ): List<IrExpression?> {
            var compositesLeft = compositeCount
            val arguments = mutableListOf<IrExpression?>()

            for ((index, arg) in oldArguments.withIndex()) {
                val value =
                    if (arg is IrComposite) {
                        compositesLeft--
                        newStatements += arg.statements.subList(0, arg.statements.lastIndex)
                        arg.statements.last() as IrExpression
                    } else {
                        arg
                    }

                val newArg =
                    when {
                        compositesLeft == 0 -> {
                            value
                        }

                        index == 0 && dontDetachFirstArgument -> {
                            value
                        }

                        value == null -> {
                            value
                        }

                        value is IrConst || value is IrGetValue -> {
                            value
                        }

                        else -> {
                            val tempVar = makeTempVar(value.type, value)
                            newStatements += tempVar
                            buildGetValue(tempVar)
                        }
                    }

                arguments += newArg
            }
            return arguments
        }

        override fun visitVararg(expression: IrVararg): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val compositeCount = expression.elements.count { it is IrComposite }
            if (compositeCount == 0) return expression

            val newStatements = mutableListOf<IrStatement>()
            val argumentsExpressions =
                mapArguments(
                    expression.elements.map { (it as? IrSpreadElement)?.expression ?: it as IrExpression },
                    compositeCount,
                    newStatements,
                )

            val arguments =
                expression.elements.withIndex().map { (i, v) ->
                    val expr = argumentsExpressions[i]!!
                    (v as? IrSpreadElement)?.run { IrSpreadElementImpl(startOffset, endOffset, expr) } ?: expr
                }

            newStatements += expression.run { IrVarargImpl(startOffset, endOffset, type, varargElementType, arguments) }
            return expression.run { IrCompositeImpl(startOffset, endOffset, type, null, newStatements) }
        }

        override fun visitMemberAccess(expression: IrMemberAccessExpression<*>): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val oldArguments = expression.arguments
            val compositeCount = oldArguments.count { it is IrComposite }
            if (compositeCount == 0) return expression

            val newStatements = mutableListOf<IrStatement>()
            val newArguments = mapArguments(oldArguments, compositeCount, newStatements)

            expression.arguments.assignFrom(newArguments)
            newStatements += expression

            return expression.run { IrCompositeImpl(startOffset, endOffset, type, origin, newStatements) }
        }

        override fun visitDynamicMemberExpression(expression: IrDynamicMemberExpression): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val composite = expression.receiver as? IrComposite ?: return expression

            return materializeLastExpression(composite) {
                expression.apply { receiver = it }
            }
        }

        override fun visitDynamicOperatorExpression(expression: IrDynamicOperatorExpression): IrExpression {
            expression.transformChildrenVoid(expressionTransformer)

            val oldArguments = listOf(expression.receiver) + expression.arguments
            val compositeCount = oldArguments.count { it is IrComposite }
            if (compositeCount == 0) return expression

            val newStatements = mutableListOf<IrStatement>()
            val newArguments =
                mapArguments(
                    oldArguments,
                    compositeCount,
                    newStatements,
                    dontDetachFirstArgument = expression.isReceiverNonDetachable(),
                )

            expression.receiver =
                newArguments[0]
                    ?: compilationException("No new receiver in destructured composite", expression)

            for (i in expression.arguments.indices) {
                expression.arguments[i] =
                    newArguments[i + 1]
                        ?: compilationException("No argument #$i in destructured composite", expression)
            }

            newStatements.add(expression)
            return expression.run { IrCompositeImpl(startOffset, endOffset, type, null, newStatements) }
        }

        private fun IrDynamicOperatorExpression.isReceiverNonDetachable(): Boolean {
            val receiver =
                when (val r = this.receiver) {
                    is IrComposite -> r.statements.lastOrNull() ?: return false
                    else -> r
                }

            val receiverIsMemberAccess =
                receiver is IrDynamicMemberExpression ||
                    (receiver is IrDynamicOperatorExpression && receiver.operator == IrDynamicOperator.ARRAY_ACCESS)

            val operatorDependsOnMemberAccess = (operator == IrDynamicOperator.INVOKE)

            return operator.isAssignmentOperator || (receiverIsMemberAccess && operatorDependsOnMemberAccess)
        }

        override fun visitContainerExpression(expression: IrContainerExpression): IrExpression {
            if (expression.statements.isEmpty()) {
                return IrCompositeImpl(expression.startOffset, expression.endOffset, expression.type, expression.origin, listOf(unitValue))
            }

            val newStatements = mutableListOf<IrStatement>()
            for (i in 0 until expression.statements.lastIndex) {
                newStatements += destructureComposite(expression.statements[i].transformStatement(statementTransformer))
            }
            newStatements += destructureComposite(expression.statements.last().transformStatement(expressionTransformer))

            return buildComposite(expression.type, newStatements)
        }

        private fun wrap(expression: IrExpression) =
            expression as? IrBlock ?: expression.let { IrBlockImpl(it.startOffset, it.endOffset, it.type, null, listOf(it)) }

        private fun wrap(
            expression: IrExpression,
            variable: IrVariable,
        ) = wrap(buildSetVariable(variable, expression))

        override fun visitTry(aTry: IrTry): IrExpression {
            val irVar = makeTempVar(aTry.type)

            val newTryResult = wrap(aTry.tryResult, irVar)
            val newCatches =
                aTry.catches.memoryOptimizedMap {
                    val newCatchBody = wrap(it.result, irVar)
                    IrCatchImpl(it.startOffset, it.endOffset, it.catchParameter, newCatchBody, it.origin)
                }

            val newTry = aTry.run { IrTryImpl(startOffset, endOffset, unitType, newTryResult, newCatches, finallyExpression) }
            newTry.transformChildrenVoid(statementTransformer)

            return buildComposite(aTry.type, listOf(irVar, newTry, buildGetValue(irVar)))
        }

        override fun visitWhen(expression: IrWhen): IrExpression {
            var hasComposites = false
            val decomposedResults =
                expression.branches.map {
                    val transformedCondition = it.condition.transform(expressionTransformer, null)
                    val transformedResult = it.result.transform(expressionTransformer, null)
                    hasComposites = hasComposites || transformedCondition is IrComposite || transformedResult is IrComposite
                    Triple(it, transformedCondition, transformedResult)
                }

            val isLogicalShortCircuit =
                expression.origin == IrStatementOrigin.ANDAND || expression.origin == IrStatementOrigin.OROR
            val shouldDecompose = hasComposites || !isLogicalShortCircuit

            if (shouldDecompose) {
                if (expression.type.isNothing()) {
                    val newBranches =
                        decomposedResults.memoryOptimizedMap { (branch, condition, result) ->
                            val newResult = wrap(result)
                            when {
                                isElseBranch(branch) -> IrElseBranchImpl(branch.startOffset, branch.endOffset, condition, newResult)
                                else -> IrBranchImpl(branch.startOffset, branch.endOffset, condition, newResult)
                            }
                        }

                    val newWhen =
                        expression
                            .run { IrWhenImpl(startOffset, endOffset, unitType, origin, newBranches) }
                            .transform(statementTransformer, null)

                    return buildComposite(expression.type, listOf(newWhen, unreachableExpression()))
                }

                if (expression.type.isUnit()) {
                    val newBranches =
                        decomposedResults.memoryOptimizedMap { (branch, condition, result) ->
                            val newResult = wrap(result)
                            when {
                                isElseBranch(branch) -> IrElseBranchImpl(branch.startOffset, branch.endOffset, condition, newResult)
                                else -> IrBranchImpl(branch.startOffset, branch.endOffset, condition, newResult)
                            }
                        }

                    val newWhen =
                        expression
                            .run { IrWhenImpl(startOffset, endOffset, unitType, origin, newBranches) }
                            .transform(statementTransformer, null)

                    return buildComposite(expression.type, listOf(newWhen, unitValue))
                }

                val irVar = makeTempVar(expression.type)

                val newBranches =
                    decomposedResults.memoryOptimizedMap { (branch, condition, result) ->
                        val newResult =
                            if (result.type.isNothing()) {
                                wrap(result)
                            } else {
                                wrap(result, irVar)
                            }
                        when {
                            isElseBranch(branch) -> IrElseBranchImpl(branch.startOffset, branch.endOffset, condition, newResult)
                            else -> IrBranchImpl(branch.startOffset, branch.endOffset, condition, newResult)
                        }
                    }

                val newWhen =
                    expression
                        .run { IrWhenImpl(startOffset, endOffset, unitType, origin, newBranches) }
                        .transform(statementTransformer, null)

                return buildComposite(expression.type, listOf(irVar, newWhen, buildGetValue(irVar)))
            } else {
                val newBranches =
                    decomposedResults.memoryOptimizedMap { (branch, condition, result) ->
                        when {
                            isElseBranch(branch) -> IrElseBranchImpl(branch.startOffset, branch.endOffset, condition, result)
                            else -> IrBranchImpl(branch.startOffset, branch.endOffset, condition, result)
                        }
                    }

                return expression.run { IrWhenImpl(startOffset, endOffset, type, origin, newBranches) }
            }
        }
    }
}
