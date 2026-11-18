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
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.types.IrDynamicType
import org.jetbrains.kotlin.ir.types.isAny
import org.jetbrains.kotlin.ir.types.isArray
import org.jetbrains.kotlin.ir.types.isPrimitiveType
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.hasShape
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.util.OperatorNameConventions

class MethodsOfAnyCallsTransformer(
    context: LuaIrBackendContext,
) : CallsTransformer {
    private val irBuiltIns = context.irBuiltIns
    private val symbols = context.symbols

    override fun transformFunctionAccess(
        call: IrFunctionAccessExpression,
        doNotIntrinsify: Boolean,
    ): IrExpression {
        if (doNotIntrinsify) return call
        val function = call.symbol.owner
        if (function.name != OperatorNameConventions.TO_STRING) return call

        if (shouldReplaceToStringWithRuntimeCall(call)) {
            return IrCallImpl(
                startOffset = call.startOffset,
                endOffset = call.endOffset,
                type = call.type,
                symbol = symbols.luaToString,
                typeArgumentsCount = 0,
                origin = call.origin,
            ).apply {
                arguments[0] = call.arguments[0]
            }
        }

        return call
    }

    private fun shouldReplaceToStringWithRuntimeCall(call: IrFunctionAccessExpression): Boolean {
        val function = call.symbol.owner

        if (function.hasShape(extensionReceiver = true, regularParameters = 0) &&
            function.fqNameWhenAvailable?.asString() == "kotlin.toString"
        ) {
            return true
        }

        if (!function.hasShape(dispatchReceiver = true, regularParameters = 0)) {
            return false
        }

        if (call is IrCall) {
            val superQualifierSymbol = call.superQualifierSymbol
            if (superQualifierSymbol != null &&
                !superQualifierSymbol.owner.isInterface &&
                superQualifierSymbol != irBuiltIns.anyClass
            ) {
                return false
            }
        }

        val receiverType = function.dispatchReceiverParameter?.type ?: return false
        return receiverType.run {
            isArray() || isAny() || this is IrDynamicType || isString() || isPrimitiveType()
        }
    }
}
