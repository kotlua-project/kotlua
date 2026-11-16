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
package io.github.kotlua.ir.lower.calls

import io.github.kotlua.ir.LuaIrBackendContext
import io.github.kotlua.name.LuaStandardClassIds
import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFieldAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrTransformer

class CallsLowering(
    val context: LuaIrBackendContext,
) : BodyLoweringPass {
    private val transformers: List<CallsTransformer> =
        listOf(
            NumberOperatorCallsTransformer(context),
            EqualityAndComparisonCallsTransformer(context),
            PrimitiveContainerMemberCallTransformer(context),
        )

    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        irBody.transformChildren(
            object : IrTransformer<IrDeclaration>() {
                override fun visitFunction(
                    declaration: IrFunction,
                    data: IrDeclaration,
                ): IrStatement = super.visitFunction(declaration, declaration)

                override fun visitFunctionAccess(
                    expression: IrFunctionAccessExpression,
                    data: IrDeclaration,
                ): IrElement {
                    val call = super.visitFunctionAccess(expression, data)
                    val doNotIntrinsify = data.hasAnnotation(LuaStandardClassIds.Annotations.DoNotIntrinsify)
                    if (call is IrFunctionAccessExpression) {
                        for (transformer in transformers) {
                            val newCall = transformer.transformFunctionAccess(call, doNotIntrinsify)
                            if (newCall !== call) {
                                return newCall
                            }
                        }
                    }
                    return call
                }

                override fun visitFieldAccess(
                    expression: IrFieldAccessExpression,
                    data: IrDeclaration,
                ): IrExpression {
                    val access = super.visitFieldAccess(expression, data)
                    if (access is IrFieldAccessExpression) {
                        for (transformer in transformers) {
                            val newAccess = transformer.transformFieldAccess(access)
                            if (newAccess !== access) {
                                return newAccess
                            }
                        }
                    }
                    return access
                }
            },
            container,
        )
    }
}

interface CallsTransformer {
    fun transformFunctionAccess(
        call: IrFunctionAccessExpression,
        doNotIntrinsify: Boolean,
    ): IrExpression

    fun transformFieldAccess(access: IrFieldAccessExpression): IrExpression = access
}

internal typealias SymbolToTransformer = HashMap<IrSymbol, (IrFunctionAccessExpression) -> IrExpression>

internal fun SymbolToTransformer.add(
    from: IrSimpleFunctionSymbol,
    to: IrSimpleFunctionSymbol,
) {
    put(from) { call ->
        IrCallImpl(
            startOffset = call.startOffset,
            endOffset = call.endOffset,
            type = call.type,
            symbol = to,
            typeArgumentsCount = 0,
            origin = call.origin,
        ).apply {
            for (i in call.arguments.indices) {
                arguments[i] = call.arguments[i]
            }
        }
    }
}

internal fun SymbolToTransformer.add(
    from: IrSimpleFunctionSymbol,
    transform: (IrFunctionAccessExpression) -> IrExpression,
) {
    put(from, transform)
}
