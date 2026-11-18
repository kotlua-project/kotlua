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
package io.github.kotlua.ir.lower.inline

import io.github.kotlua.ir.LuaIrBackendContext
import org.jetbrains.kotlin.backend.common.DeclarationTransformer
import org.jetbrains.kotlin.backend.common.LoweringContext
import org.jetbrains.kotlin.backend.common.ir.isInlineFunWithReifiedParameter
import org.jetbrains.kotlin.backend.common.phaser.PhasePrerequisites
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.inline.FunctionInlining
import org.jetbrains.kotlin.ir.inline.InlineFunctionResolverReplacingCoroutineIntrinsics
import org.jetbrains.kotlin.ir.inline.InlineMode
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols

internal class LuaInlineFunctionResolver(
    context: LoweringContext,
    inlineMode: InlineMode,
) : InlineFunctionResolverReplacingCoroutineIntrinsics<LoweringContext>(context, inlineMode)

internal class LuaPrivateFunctionInlining(
    context: LuaIrBackendContext,
) : FunctionInlining(
        context,
        LuaInlineFunctionResolver(context, inlineMode = InlineMode.PRIVATE_INLINE_FUNCTIONS),
    )

internal class LuaAllFunctionInlining(
    context: LuaIrBackendContext,
) : FunctionInlining(
        context,
        LuaInlineFunctionResolver(context, inlineMode = InlineMode.ALL_INLINE_FUNCTIONS),
    )

@PhasePrerequisites(FunctionInlining::class)
class RemoveInlineDeclarationsWithReifiedTypeParametersLowering(
    @Suppress("unused") context: LoweringContext,
) : DeclarationTransformer {
    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        if ((declaration is IrFunction && declaration.isInlineFunWithReifiedParameter()) ||
            (declaration is IrProperty && (declaration.getter?.isInlineFunWithReifiedParameter() == true))
        ) {
            return emptyList()
        }
        return null
    }
}

@PhasePrerequisites(FunctionInlining::class)
class CopyInlineFunctionBodyLowering(
    val context: LuaIrBackendContext,
) : DeclarationTransformer {
    override fun transformFlat(declaration: IrDeclaration): List<IrDeclaration>? {
        if (declaration is IrFunction && declaration.isInline) {
            declaration.body?.let { originalBody ->
                declaration.body =
                    context.irFactory.createBlockBody(originalBody.startOffset, originalBody.endOffset) {
                        statements += (originalBody.deepCopyWithSymbols(declaration) as IrBlockBody).statements
                    }
            }
        }

        if (declaration is IrValueParameter && declaration.parent.let { it is IrFunction && it.isInline }) {
            declaration.defaultValue?.let { originalDefault ->
                declaration.defaultValue =
                    context.irFactory.createExpressionBody(
                        startOffset = originalDefault.startOffset,
                        endOffset = originalDefault.endOffset,
                        expression = originalDefault.expression.deepCopyWithSymbols(declaration.parent),
                    )
            }
        }

        return null
    }
}
