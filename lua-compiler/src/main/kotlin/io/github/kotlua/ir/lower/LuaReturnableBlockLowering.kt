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

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.backend.common.lower.at
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irComposite
import org.jetbrains.kotlin.backend.common.phaser.PhasePrerequisites
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.at
import org.jetbrains.kotlin.ir.builders.irComposite
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrReturnableBlock
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.inline.FunctionInlining
import org.jetbrains.kotlin.ir.symbols.IrReturnableBlockSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

@PhasePrerequisites(FunctionInlining::class)
class LuaReturnableBlockLowering(
    val context: CommonBackendContext,
) : BodyLoweringPass {
    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        container.transform(LuaReturnableBlockTransformer(context), null)
    }
}

private class LuaReturnableBlockTransformer(
    val context: CommonBackendContext,
) : IrElementTransformerVoidWithContext() {
    private var labelCnt = 0
    private val map = hashMapOf<IrReturnableBlockSymbol, IrVariable>()

    private val unitType = context.irBuiltIns.unitType
    private val unitValue
        get() = IrGetObjectValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, unitType, context.irBuiltIns.unitClass)

    private inline fun withNewVariableForReturnableBlock(
        returnableBlock: IrReturnableBlock,
        action: (IrVariable) -> IrExpression,
    ): IrExpression {
        val variable =
            currentScope!!
                .scope
                .createTemporaryVariable(
                    irExpression = returnableBlock,
                    nameHint = "tmp\$ret\$${labelCnt++}",
                    isMutable = true,
                    startOffset = UNDEFINED_OFFSET,
                    endOffset = UNDEFINED_OFFSET,
                    inventUniqueName = false,
                ).apply {
                    initializer = null
                }

        val previousVariable = map.put(returnableBlock.symbol, variable)
        check(previousVariable == null) { "Returnable block is already mapped to another variable: $previousVariable" }

        val result = action(variable)

        val removedVariable = map.remove(returnableBlock.symbol)
        check(removedVariable == variable) { "Removed incorrect variable: $removedVariable, expected: $variable" }

        return result
    }

    private fun collectReturnsToBlock(block: IrReturnableBlock): List<IrReturn> {
        val returns = mutableListOf<IrReturn>()
        block.acceptChildrenVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitFunction(declaration: IrFunction) = Unit

                override fun visitClass(declaration: IrClass) = Unit

                override fun visitReturn(expression: IrReturn) {
                    if (expression.returnTargetSymbol == block.symbol) {
                        returns += expression
                    }
                    super.visitReturn(expression)
                }
            },
        )
        return returns
    }

    private fun isSingleTailReturn(
        container: IrContainerExpression,
        targetReturn: IrReturn,
    ): Boolean {
        var current: IrContainerExpression = container
        while (true) {
            val last = current.statements.lastOrNull() ?: return false
            if (last === targetReturn) return true
            if (last is IrContainerExpression && last !is IrReturnableBlock) {
                current = last
            } else {
                return false
            }
        }
    }

    private fun transformTailContainer(
        container: IrContainerExpression,
        targetReturn: IrReturn,
        targetType: IrType,
    ): IrCompositeImpl {
        val lastIndex = container.statements.lastIndex
        val newStatements = ArrayList<IrStatement>(container.statements.size)
        for (i in 0 until lastIndex) {
            newStatements += container.statements[i].transform(this, null) as IrStatement
        }

        val last = container.statements[lastIndex]
        if (last === targetReturn) {
            val transformedValue = targetReturn.value.transform(this, null)
            val resultValue =
                if (transformedValue.type == targetType || transformedValue.type.isNothing()) {
                    transformedValue
                } else {
                    IrTypeOperatorCallImpl(
                        startOffset = transformedValue.startOffset,
                        endOffset = transformedValue.endOffset,
                        type = targetType,
                        operator = IrTypeOperator.REINTERPRET_CAST,
                        typeOperand = targetType,
                        argument = transformedValue,
                    )
                }
            newStatements += resultValue
        } else {
            val innerComposite = transformTailContainer(last as IrContainerExpression, targetReturn, targetType)
            newStatements += innerComposite.statements
        }

        return IrCompositeImpl(
            container.startOffset,
            container.endOffset,
            targetType,
            container.origin,
            newStatements,
        )
    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        if (expression !is IrReturnableBlock) return super.visitBlock(expression)

        val returnsToBlock = collectReturnsToBlock(expression)

        if (returnsToBlock.isEmpty()) {
            expression.transformChildrenVoid()
            return IrCompositeImpl(
                expression.startOffset,
                expression.endOffset,
                expression.type,
                expression.origin,
                expression.statements,
            )
        }

        if (returnsToBlock.size == 1 && isSingleTailReturn(expression, returnsToBlock[0])) {
            return transformTailContainer(expression, returnsToBlock[0], expression.type)
        }

        return withNewVariableForReturnableBlock(returnableBlock = expression) { variable ->
            expression.transformChildrenVoid()

            val builder = context.createIrBuilder(expression.symbol)

            expression.type = unitType
            return builder.irComposite(expression, expression.origin, variable.type) {
                +variable
                +expression
                +irGet(variable)
            }
        }
    }

    override fun visitReturn(expression: IrReturn): IrExpression {
        expression.transformChildrenVoid()

        val targetSymbol = expression.returnTargetSymbol
        if (targetSymbol !is IrReturnableBlockSymbol) return expression

        val variable = map[targetSymbol] ?: compilationException("Unmapped returnable block $targetSymbol", expression)

        val builder = context.createIrBuilder(targetSymbol)
        return builder.at(UNDEFINED_OFFSET, UNDEFINED_OFFSET).irComposite {
            +at(expression).irSet(variable.symbol, expression.value)
            +at(UNDEFINED_OFFSET, UNDEFINED_OFFSET).irReturn(unitValue)
        }
    }
}
