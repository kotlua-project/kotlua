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
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isString
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class LuaStringConcatenationLowering(
    private val context: LuaIrBackendContext,
) : FileLoweringPass {
    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(LuaStringConcatenationTransformer(context))
    }
}

private class LuaStringConcatenationTransformer(
    private val context: LuaIrBackendContext,
) : IrElementTransformerVoid() {
    private val memberStringPlus: IrSimpleFunctionSymbol? =
        context.irBuiltIns.stringClass.owner.functions
            .firstOrNull { it.name.asString() == "plus" && it.parameters.size == 2 }
            ?.symbol

    private val IrType.shouldExplicitlyConvertToString: Boolean
        get() = !(isString() && !isNullable())

    private fun IrExpression.wrapWithToString(): IrExpression {
        if (!type.shouldExplicitlyConvertToString) return this
        return IrCallImpl(
            startOffset = startOffset,
            endOffset = endOffset,
            type = context.irBuiltIns.stringType,
            symbol = context.symbols.luaToString,
            typeArgumentsCount = 0,
        ).apply {
            arguments[0] = this@wrapWithToString
        }
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val transformed = super.visitCall(expression) as? IrCall ?: return expression
        if (transformed.symbol == memberStringPlus) {
            val lastArgIndex = transformed.arguments.lastIndex
            if (lastArgIndex >= 0) {
                transformed.arguments[lastArgIndex]?.let { arg ->
                    transformed.arguments[lastArgIndex] = arg.wrapWithToString()
                }
            }
        }
        return transformed
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation): IrExpression {
        val transformed = super.visitStringConcatenation(expression) as? IrStringConcatenation ?: return expression
        for (i in transformed.arguments.indices) {
            transformed.arguments[i] = transformed.arguments[i].wrapWithToString()
        }
        return transformed
    }
}
