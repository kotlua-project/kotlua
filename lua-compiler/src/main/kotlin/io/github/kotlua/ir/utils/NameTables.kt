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
package io.github.kotlua.ir.utils

import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.resolveFakeOverride
import org.jetbrains.kotlin.ir.util.superTypes
import kotlin.math.abs

private const val RESERVED_MEMBER_NAME_SUFFIX = "_k"

private fun List<IrType>.joinTypes(): String {
    if (isEmpty()) return ""
    return joinToString("$", "$") { it.render() }
}

fun IrDeclarationWithName.nameIfPropertyAccessor(): String? {
    if (this !is IrSimpleFunction) return null
    val property = correspondingPropertySymbol?.owner ?: return null
    val propName = property.name.asString()
    val prefix =
        when (this) {
            property.getter -> "get_"
            property.setter -> "set_"
            else -> return null
        }
    return prefix + propName
}

fun calculateLuaFunctionSignature(declaration: IrFunction): String {
    val nameBuilder = StringBuilder()
    val declarationName = declaration.nameIfPropertyAccessor() ?: declaration.name.asString()

    nameBuilder.append(declarationName)

    if (declaration.visibility == DescriptorVisibilities.INTERNAL && declaration.parentClassOrNull != null) {
        val containingModule = (declaration.getPackageFragment() as? IrFile)?.module
        if (containingModule != null) {
            nameBuilder.append("_\$m_").append(containingModule.name.asString())
        }
    }

    if (declaration.typeParameters.isNotEmpty()) {
        nameBuilder.append("_\$t")
        for (typeParam in declaration.typeParameters) {
            nameBuilder.append("_").append(typeParam.name.asString()).append(typeParam.superTypes.joinTypes())
        }
    }

    for (parameter in declaration.parameters) {
        when (parameter.kind) {
            IrParameterKind.DispatchReceiver -> continue
            IrParameterKind.ExtensionReceiver -> nameBuilder.append("_r$")
            IrParameterKind.Context -> nameBuilder.append("_c$")
            IrParameterKind.Regular -> nameBuilder.append("_")
        }
        nameBuilder.append(parameter.type.render())
        nameBuilder.append(parameter.type.superTypes().joinTypes())
    }

    if (declaration.returnType.isUnit()) {
        nameBuilder.append("_ret\$Unit")
    }

    val signatureHash = abs(nameBuilder.toString().hashCode()).toString(Character.MAX_RADIX)
    val sanitizedName = makeValidLuaIdentifier(declarationName)
    return "${sanitizedName}_${signatureHash}$RESERVED_MEMBER_NAME_SUFFIX"
}

fun luaFunctionSignature(declaration: IrFunction): String {
    require(declaration.dispatchReceiverParameter != null) {
        "Member function '${declaration.name}' must have dispatchReceiverParameter"
    }
    val targetDeclaration = (declaration as? IrSimpleFunction)?.resolveFakeOverride() ?: declaration
    return calculateLuaFunctionSignature(targetDeclaration)
}
