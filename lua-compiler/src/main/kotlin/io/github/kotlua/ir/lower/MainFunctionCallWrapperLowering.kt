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
import io.github.kotlua.ir.utils.LuaMainFunctionDetector
import io.github.kotlua.ir.utils.isStringArrayParameter
import org.jetbrains.kotlin.backend.common.ModuleLoweringPass
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOriginImpl
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.util.file
import org.jetbrains.kotlin.name.Name

private val MAIN_WRAPPER_ORIGIN by IrDeclarationOriginImpl.Regular

var IrSimpleFunction.mainFunctionWrapper: IrSimpleFunction? by irAttribute(copyByDefault = false)

class MainFunctionCallWrapperLowering(
    private val context: LuaIrBackendContext,
) : ModuleLoweringPass {
    private val mainFunctionDetector = LuaMainFunctionDetector(context)

    override fun lower(irModule: IrModuleFragment) {
        if (!context.callMain) return

        val mainFunction = mainFunctionDetector.getMainFunctionOrNull(irModule) ?: return
        val wrapper = mainFunction.generateWrapperForMainFunction()
        mainFunction.mainFunctionWrapper = wrapper
        mainFunction.file.declarations.add(wrapper)
    }

    private fun IrSimpleFunction.generateWrapperForMainFunction(): IrSimpleFunction {
        val originalSymbol = symbol
        val takesArgs = hasStringArrayParameter()

        return context.irFactory
            .createSimpleFunction(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                origin = MAIN_WRAPPER_ORIGIN,
                name = Name.identifier("mainWrapper"),
                visibility = visibility,
                isInline = false,
                isExpect = false,
                returnType = returnType,
                modality = modality,
                symbol = IrSimpleFunctionSymbolImpl(),
                isTailrec = false,
                isSuspend = false,
                isOperator = false,
                isInfix = false,
            ).also { wrapper ->
                wrapper.parent = parent
                wrapper.body =
                    context.irFactory.createBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET).apply {
                        val call =
                            IrCallImpl(
                                startOffset = UNDEFINED_OFFSET,
                                endOffset = UNDEFINED_OFFSET,
                                type = context.irBuiltIns.unitType,
                                symbol = originalSymbol,
                                typeArgumentsCount = 0,
                            ).apply {
                                if (takesArgs) {
                                    arguments[0] =
                                        IrCallImpl(
                                            startOffset = UNDEFINED_OFFSET,
                                            endOffset = UNDEFINED_OFFSET,
                                            type = parameters.first { it.kind == IrParameterKind.Regular }.type,
                                            symbol = context.symbols.luaMainArgs,
                                            typeArgumentsCount = 0,
                                        )
                                }
                            }
                        statements.add(call)
                    }
            }
    }

    private fun IrSimpleFunction.hasStringArrayParameter(): Boolean =
        parameters.firstOrNull { it.kind == IrParameterKind.Regular }?.isStringArrayParameter() == true
}
