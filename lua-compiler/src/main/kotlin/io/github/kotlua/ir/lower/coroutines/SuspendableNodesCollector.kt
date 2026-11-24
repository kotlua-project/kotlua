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

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBreakContinue
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrReturnableBlockSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.isSuspend
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

open class SuspendableNodesCollector(
    private val suspendableNodes: MutableSet<IrElement>,
) : IrVisitorVoid() {
    private var hasSuspendableChildren = false

    private fun markNode(node: IrElement) {
        suspendableNodes += node
        hasSuspendableChildren = true
    }

    private fun isSuspendableNode(node: IrElement) = node in suspendableNodes

    override fun visitElement(element: IrElement) {
        val current = hasSuspendableChildren
        hasSuspendableChildren = false
        element.acceptChildrenVoid(this)
        if (hasSuspendableChildren) {
            markNode(element)
        }
        hasSuspendableChildren = hasSuspendableChildren || current
    }

    override fun visitCall(expression: IrCall) {
        super.visitCall(expression)
        if (expression.isSuspend) {
            markNode(expression)
        }
    }

    override fun visitBreakContinue(jump: IrBreakContinue) {
        if (isSuspendableNode(jump.loop)) {
            markNode(jump)
        }
    }

    override fun visitReturn(expression: IrReturn) {
        super.visitReturn(expression)
        if (expression.returnTargetSymbol is IrReturnableBlockSymbol && isSuspendableNode(expression.returnTargetSymbol.owner)) {
            markNode(expression)
        }
    }
}

fun collectSuspendableNodes(function: IrBlock): MutableSet<IrElement> {
    val suspendableNodes = mutableSetOf<IrElement>()
    var size: Int
    do {
        size = suspendableNodes.size
        function.acceptVoid(SuspendableNodesCollector(suspendableNodes))
    } while (size != suspendableNodes.size)
    return suspendableNodes
}

class LiveLocalsTransformer(
    private val localMap: Map<IrValueSymbol, IrFieldSymbol>,
    private val receiver: () -> IrExpression,
    private val unitType: IrType,
) : IrElementTransformerVoid() {
    override fun visitGetValue(expression: IrGetValue): IrExpression {
        val field =
            localMap[expression.symbol]
                ?: return if ((expression.symbol.owner as? IrValueParameter)?.kind == IrParameterKind.DispatchReceiver) {
                    receiver()
                } else {
                    expression
                }

        return IrGetFieldImpl(
            expression.startOffset,
            expression.endOffset,
            field,
            expression.type,
            receiver(),
            expression.origin,
        )
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        expression.transformChildrenVoid(this)
        val field = localMap[expression.symbol] ?: return expression
        return IrSetFieldImpl(
            expression.startOffset,
            expression.endOffset,
            field,
            receiver(),
            expression.value,
            unitType,
            expression.origin,
        )
    }

    override fun visitVariable(declaration: IrVariable): IrStatement {
        declaration.transformChildrenVoid(this)
        val field = localMap[declaration.symbol] ?: return declaration
        val initializer = declaration.initializer
        return if (initializer != null) {
            IrSetFieldImpl(
                declaration.startOffset,
                declaration.endOffset,
                field,
                receiver(),
                initializer,
                unitType,
            )
        } else {
            IrCompositeImpl(declaration.startOffset, declaration.endOffset, unitType)
        }
    }
}
