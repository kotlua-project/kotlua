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

import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.backend.common.lower.InnerClassesSupport
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.builders.declarations.buildConstructor
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.copyTypeParametersFrom
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.getOrSetIfNull
import org.jetbrains.kotlin.utils.memoryOptimizedPlus

private var IrClass.outerThisFieldSymbol: IrField? by irAttribute(copyByDefault = false)
private var IrConstructor.innerClassConstructor: IrConstructor? by irAttribute(copyByDefault = false)
private var IrClass.originalInnerClassPrimaryConstructor: IrConstructor? by irAttribute(copyByDefault = false)

class LuaInnerClassesSupport(
    private val irFactory: IrFactory,
) : InnerClassesSupport {
    override fun getOuterThisField(innerClass: IrClass): IrField =
        if (!innerClass.isInner) {
            compilationException("Class is not inner", innerClass)
        } else {
            innerClass::outerThisFieldSymbol.getOrSetIfNull {
                val outerClass =
                    innerClass.parent as? IrClass
                        ?: compilationException("No containing class for inner class", innerClass)

                irFactory
                    .buildField {
                        origin = IrDeclarationOrigin.FIELD_FOR_OUTER_THIS
                        name = Name.identifier("\$this")
                        type = outerClass.defaultType
                        visibility = DescriptorVisibilities.PROTECTED
                        isFinal = true
                        isExternal = false
                        isStatic = false
                    }.also {
                        it.parent = innerClass
                    }
            }
        }

    override fun getInnerClassConstructorWithOuterThisParameter(innerClassConstructor: IrConstructor): IrConstructor {
        val innerClass = innerClassConstructor.parent as IrClass
        assert(innerClass.isInner) { "Class is not inner: $innerClass" }

        return innerClassConstructor::innerClassConstructor
            .getOrSetIfNull {
                createInnerClassConstructorWithOuterThisParameter(innerClassConstructor)
            }.also {
                if (innerClassConstructor.isPrimary) {
                    innerClass.originalInnerClassPrimaryConstructor = innerClassConstructor
                }
            }
    }

    override fun getInnerClassOriginalPrimaryConstructorOrNull(innerClass: IrClass): IrConstructor? {
        assert(innerClass.isInner) { "Class is not inner: $innerClass" }
        return innerClass.originalInnerClassPrimaryConstructor
    }

    private fun createInnerClassConstructorWithOuterThisParameter(oldConstructor: IrConstructor): IrConstructor {
        val irClass = oldConstructor.parent as IrClass
        val outerThisType = (irClass.parent as IrClass).defaultType

        val newConstructor =
            irFactory
                .buildConstructor {
                    updateFrom(oldConstructor)
                    returnType = oldConstructor.returnType
                }.also {
                    it.parent = oldConstructor.parent
                    it.annotations = oldConstructor.annotations
                }

        newConstructor.copyTypeParametersFrom(oldConstructor)

        val newValueParameters =
            mutableListOf(
                buildValueParameter(newConstructor) {
                    origin = IrDeclarationOrigin.DEFINED
                    name = Name.identifier("\$outer")
                    type = outerThisType
                    kind = IrParameterKind.Regular
                },
            )

        for (p in oldConstructor.nonDispatchParameters) {
            newValueParameters += p.copyTo(newConstructor)
        }

        newConstructor.parameters = newConstructor.parameters memoryOptimizedPlus newValueParameters

        return newConstructor
    }
}
