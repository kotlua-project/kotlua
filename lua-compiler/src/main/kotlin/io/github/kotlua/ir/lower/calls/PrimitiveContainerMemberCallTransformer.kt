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
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter

class PrimitiveContainerMemberCallTransformer(
    context: LuaIrBackendContext,
) : CallsTransformer {
    private val symbolToTransformer: SymbolToTransformer = hashMapOf()

    init {
        val irBuiltIns = context.irBuiltIns
        val symbols = context.symbols

        symbolToTransformer.run {
            irBuiltIns.stringClass.getPropertyGetter("length")?.let {
                add(it, symbols.luaLength)
            }
            irBuiltIns.stringClass.owner.functions
                .firstOrNull { it.name.asString() == "plus" && it.parameters.size == 2 }
                ?.let { add(it.symbol, symbols.luaConcat) }
        }
    }

    override fun transformFunctionAccess(
        call: IrFunctionAccessExpression,
        doNotIntrinsify: Boolean,
    ): IrExpression {
        if (doNotIntrinsify) return call
        return symbolToTransformer[call.symbol]?.invoke(call) ?: call
    }
}
