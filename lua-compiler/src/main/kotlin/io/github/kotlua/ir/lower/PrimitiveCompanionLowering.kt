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
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.irCall
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class PrimitiveCompanionLowering(
    val context: LuaIrBackendContext,
) : BodyLoweringPass {
    private fun getActualPrimitiveCompanion(irClass: IrClass): IrClass? {
        if (!irClass.isCompanion) return null

        val parent = irClass.parent as? IrClass ?: return null
        if (!parent.defaultType.isPrimitiveType() && !parent.defaultType.isString()) return null

        return context.symbols.primitiveCompanionObjects[parent.name]?.owner
    }

    private fun getActualPrimitiveCompanionPropertyAccessor(function: IrSimpleFunction): IrSimpleFunction? {
        val property = function.correspondingPropertySymbol?.owner ?: return null
        val companion = property.parent as? IrClass ?: return null
        val actualCompanion = getActualPrimitiveCompanion(companion) ?: return null

        for (p in actualCompanion.properties) {
            p.getter?.let { if (it.name == function.name) return it }
            p.setter?.let { if (it.name == function.name) return it }
        }

        return actualCompanion.declarations
            .filterIsInstance<IrSimpleFunction>()
            .single { it.name == function.name }
    }

    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        irBody.transformChildrenVoid(
            object : IrElementTransformerVoid() {
                override fun visitGetObjectValue(expression: IrGetObjectValue): IrExpression {
                    val irClass = expression.symbol.owner
                    val actualCompanion = getActualPrimitiveCompanion(irClass) ?: return expression
                    return IrGetObjectValueImpl(
                        expression.startOffset,
                        expression.endOffset,
                        actualCompanion.defaultType,
                        actualCompanion.symbol,
                    )
                }

                override fun visitCall(expression: IrCall): IrExpression {
                    val newCall = super.visitCall(expression) as IrCall
                    val actualFunction =
                        getActualPrimitiveCompanionPropertyAccessor(expression.symbol.owner)
                            ?: return newCall
                    return irCall(newCall, actualFunction)
                }
            },
        )
    }
}
