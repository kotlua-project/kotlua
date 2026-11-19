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
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrRichFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name

class LuaCallableReferenceLowering(
    private val context: LuaIrBackendContext,
) : BodyLoweringPass {
    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        val rootParent = container as? IrDeclarationParent ?: container.parent
        irBody.transformChildren(Transformer(rootParent), null)
        irBody.patchDeclarationParents(rootParent)
    }

    private class Transformer(
        rootParent: IrDeclarationParent,
    ) : IrElementTransformerVoid() {
        private val parentStack = mutableListOf(rootParent)

        private val currentParent: IrDeclarationParent
            get() = parentStack.last()

        override fun visitFunction(declaration: IrFunction): IrStatement {
            parentStack.add(declaration)
            try {
                return super.visitFunction(declaration)
            } finally {
                parentStack.removeLast()
            }
        }

        override fun visitClass(declaration: IrClass): IrStatement {
            parentStack.add(declaration)
            try {
                return super.visitClass(declaration)
            } finally {
                parentStack.removeLast()
            }
        }

        override fun visitRichFunctionReference(expression: IrRichFunctionReference): IrExpression {
            val invokeFunction = expression.invokeFunction

            parentStack.add(invokeFunction)
            try {
                expression.boundValues.indices.forEach { i ->
                    expression.boundValues[i] = expression.boundValues[i].transform(this, null)
                }
                invokeFunction.body = invokeFunction.body?.transform(this, null)
            } finally {
                parentStack.removeLast()
            }

            invokeFunction.origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
            invokeFunction.visibility = DescriptorVisibilities.LOCAL
            invokeFunction.parent = currentParent

            val functionExpression =
                IrFunctionExpressionImpl(
                    startOffset = expression.startOffset,
                    endOffset = expression.endOffset,
                    type = expression.type,
                    function = invokeFunction,
                    origin = expression.origin ?: IrStatementOrigin.LAMBDA,
                )

            val boundCount = expression.boundValues.size
            if (boundCount == 0) {
                return functionExpression
            }

            val boundParams = invokeFunction.parameters.take(boundCount)
            val remainingParams = invokeFunction.parameters.drop(boundCount)

            val boundVars =
                expression.boundValues.mapIndexed { index, boundExpr ->
                    val param = boundParams[index]
                    IrVariableImpl(
                        startOffset = boundExpr.startOffset,
                        endOffset = boundExpr.endOffset,
                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        symbol = IrVariableSymbolImpl(),
                        name = Name.identifier("bound\$${param.name.asString()}"),
                        type = param.type,
                        isVar = false,
                        isConst = false,
                        isLateinit = false,
                    ).apply {
                        parent = currentParent
                        initializer = boundExpr
                    }
                }

            val paramToVar = boundParams.zip(boundVars).associate { (p, v) -> p.symbol to v.symbol }
            invokeFunction.parameters = remainingParams

            invokeFunction.body?.transformChildren(
                object : IrElementTransformerVoid() {
                    override fun visitGetValue(expression: IrGetValue): IrExpression {
                        val replacement = paramToVar[expression.symbol] ?: return super.visitGetValue(expression)
                        return IrGetValueImpl(
                            startOffset = expression.startOffset,
                            endOffset = expression.endOffset,
                            type = expression.type,
                            symbol = replacement,
                            origin = expression.origin,
                        )
                    }
                },
                null,
            )

            return IrBlockImpl(
                startOffset = expression.startOffset,
                endOffset = expression.endOffset,
                type = expression.type,
                origin = expression.origin,
                statements = boundVars + functionExpression,
            )
        }
    }
}
