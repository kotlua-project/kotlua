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
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.util.isEnumClass
import org.jetbrains.kotlin.ir.util.isStatic
import org.jetbrains.kotlin.ir.util.simpleFunctions
import org.jetbrains.kotlin.name.Name

class EnumIntrinsicsTransformer(
    private val context: LuaIrBackendContext,
) : CallsTransformer {
    override fun transformFunctionAccess(
        call: IrFunctionAccessExpression,
        doNotIntrinsify: Boolean,
    ): IrExpression {
        if (call.symbol == context.symbols.enumEntriesIntrinsic) {
            return transformEnumEntriesIntrinsic(call)
        }
        return call
    }

    private fun transformEnumEntriesIntrinsic(call: IrFunctionAccessExpression): IrExpression {
        val enumClass = call.typeArguments[0]?.getClass() ?: return call
        if (!enumClass.isEnumClass) return call

        val getter =
            enumClass.simpleFunctions().firstOrNull {
                it.name == Name.special("<get-entries>") && it.isStatic
            } ?: compilationException(
                "Enum class ${enumClass.name} should have static <get-entries> method",
                call,
            )

        return IrCallImpl(
            startOffset = call.startOffset,
            endOffset = call.endOffset,
            type = call.type,
            symbol = getter.symbol,
            typeArgumentsCount = getter.typeParameters.size,
            origin = call.origin,
        )
    }
}
