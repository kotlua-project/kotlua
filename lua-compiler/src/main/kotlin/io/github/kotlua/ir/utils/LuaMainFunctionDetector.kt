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

import io.github.kotlua.ir.LuaIrBackendContext
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.isArray
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.hasShape
import org.jetbrains.kotlin.types.Variance

class LuaMainFunctionDetector(
    private val context: LuaIrBackendContext,
) {
    private fun IrSimpleFunction.isMain(allowEmptyParameters: Boolean): Boolean {
        if (typeParameters.isNotEmpty()) return false
        if (name.asString() != "main") return false

        val regularParams = parameters.filter { it.kind == IrParameterKind.Regular }
        val isLoweredSuspendMain = regularParams.lastOrNull()?.origin == IrDeclarationOrigin.CONTINUATION
        val logicalParams = if (isLoweredSuspendMain) regularParams.dropLast(1) else regularParams

        if (!isLoweredSuspendMain && !returnType.isUnit()) return false
        if (logicalParams.size > 1) return false
        if (logicalParams.isEmpty() && !allowEmptyParameters) return false

        return if (logicalParams.size == 1) {
            logicalParams[0].isStringArrayParameter()
        } else {
            val file = parent as? IrFile ?: return false
            file.declarations.filterIsInstance<IrSimpleFunction>().none { it.isMain(allowEmptyParameters = false) }
        }
    }

    fun getMainFunctionOrNull(file: IrFile): IrSimpleFunction? =
        file.declarations.filterIsInstance<IrSimpleFunction>().singleOrNull { it.isMain(allowEmptyParameters = true) }

    fun getMainFunctionOrNull(module: IrModuleFragment): IrSimpleFunction? {
        var resultPair: Pair<String, IrSimpleFunction>? = null
        for (file in module.files) {
            val fqn = file.packageFqName.asString()
            getMainFunctionOrNull(file)?.let { mainFn ->
                val current = resultPair
                if (current == null || fqn < current.first) {
                    resultPair = fqn to mainFn
                }
            }
        }
        return resultPair?.second
    }
}

fun IrValueParameter.isStringArrayParameter(): Boolean {
    val type = this.type as? IrSimpleType ?: return false
    if (!type.isArray() || type.arguments.size != 1) return false
    val argument = type.arguments.single() as? IrTypeProjection ?: return false
    return argument.variance != Variance.IN_VARIANCE && argument.type.isString()
}
