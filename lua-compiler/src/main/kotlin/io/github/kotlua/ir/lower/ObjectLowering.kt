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
import org.jetbrains.kotlin.backend.common.DeclarationTransformer
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlockBody
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.backend.common.phaser.PhasePrerequisites
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrTypeOperatorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.getOrSetIfNull

private val OBJECT_GET_INSTANCE_FUNCTION by IrDeclarationOriginImpl.Regular

private var IrClass.objectInstanceField: IrField? by irAttribute(copyByDefault = false)
private var IrClass.objectGetInstanceFunction: IrSimpleFunction? by irAttribute(copyByDefault = false)

private fun IrClass.isLowerableObject(context: LuaIrBackendContext): Boolean =
    kind == ClassKind.OBJECT &&
        symbol != context.irBuiltIns.unitClass &&
        !isExpect &&
        !isEffectivelyExternal()

private fun getOrCreateInstanceField(obj: IrClass): IrField =
    obj::objectInstanceField.getOrSetIfNull {
        obj.factory
            .buildField {
                name = Name.identifier("${obj.name}\$instance")
                type = obj.defaultType.makeNullable()
                isStatic = true
                origin = IrDeclarationOrigin.FIELD_FOR_OBJECT_INSTANCE
            }.apply {
                parent = obj
                initializer = null
            }
    }

private fun getOrCreateGetInstanceFunction(obj: IrClass): IrSimpleFunction =
    obj::objectGetInstanceFunction.getOrSetIfNull {
        getOrCreateInstanceField(obj)
        obj.factory
            .buildFun {
                name = Name.identifier("${obj.name}\$getInstance")
                returnType = obj.defaultType
                origin = OBJECT_GET_INSTANCE_FUNCTION
                visibility = obj.visibility
            }.apply {
                parent = obj
            }
    }

class ObjectDeclarationLowering(
    val context: LuaIrBackendContext,
) : DeclarationTransformer {
    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        if (declaration !is IrClass || !declaration.isLowerableObject(context)) {
            return null
        }

        val instanceField = getOrCreateInstanceField(declaration)
        val getInstanceFun = getOrCreateGetInstanceFunction(declaration)
        val primaryConstructor = declaration.primaryConstructor ?: declaration.constructors.firstOrNull()

        getInstanceFun.body =
            context.irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET) {
                statements +=
                    context
                        .createIrBuilder(getInstanceFun.symbol)
                        .irBlockBody(getInstanceFun) {
                            if (primaryConstructor != null) {
                                val ctorCall =
                                    IrConstructorCallImpl.fromSymbolOwner(
                                        startOffset = UNDEFINED_OFFSET,
                                        endOffset = UNDEFINED_OFFSET,
                                        type = declaration.defaultType,
                                        constructorSymbol = primaryConstructor.symbol,
                                    )
                                +irIfThen(
                                    irEqualsNull(irGetField(null, instanceField)),
                                    irSetField(null, instanceField, ctorCall),
                                )
                            }
                            +irReturn(
                                IrTypeOperatorCallImpl(
                                    UNDEFINED_OFFSET,
                                    UNDEFINED_OFFSET,
                                    declaration.defaultType,
                                    IrTypeOperator.REINTERPRET_CAST,
                                    declaration.defaultType,
                                    irGetField(null, instanceField),
                                ),
                            )
                        }.statements
            }

        declaration.declarations.addAll(0, listOf(instanceField, getInstanceFun))
        return null
    }
}

@PhasePrerequisites(ObjectDeclarationLowering::class)
class ObjectUsageLowering(
    val context: LuaIrBackendContext,
) : BodyLoweringPass {
    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        if (container is IrConstructor && container.isPrimary) {
            val irClass = container.parentAsClass
            val instanceField = irClass.objectInstanceField
            if (instanceField != null) {
                val blockBody = irBody as IrBlockBody
                val index =
                    blockBody.statements.indexOfFirst {
                        it is IrDelegatingConstructorCall ||
                            (it is IrTypeOperatorCall && it.argument is IrDelegatingConstructorCall)
                    } + 1

                blockBody.statements.add(
                    index,
                    context.createIrBuilder(container.symbol).run {
                        irSetField(null, instanceField, irGet(irClass.thisReceiver!!))
                    },
                )
            }
        }

        irBody.transformChildrenVoid(
            object : IrElementTransformerVoid() {
                override fun visitGetObjectValue(expression: IrGetObjectValue): IrExpression {
                    val obj = expression.symbol.owner
                    if (!obj.isLowerableObject(context)) {
                        return expression
                    }
                    return IrCallImpl(
                        startOffset = expression.startOffset,
                        endOffset = expression.endOffset,
                        type = expression.type,
                        symbol = getOrCreateGetInstanceFunction(obj).symbol,
                        typeArgumentsCount = 0,
                    )
                }
            },
        )
    }
}
