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
import io.github.kotlua.ir.utils.luaFunctionSignature
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.utils.realOverrideTarget
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.allOverridden
import org.jetbrains.kotlin.ir.util.copyTo
import org.jetbrains.kotlin.ir.util.copyTypeParametersFrom
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.implicitCastIfNeededTo
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.passTypeArgumentsFrom
import org.jetbrains.kotlin.ir.util.remapTypeParameters

class LuaBridgeLowering(
    private val context: LuaIrBackendContext,
) : FileLoweringPass {
    private val irBuiltIns = context.irBuiltIns

    override fun lower(irFile: IrFile) {
        for (decl in irFile.declarations) {
            if (decl is IrClass) {
                processClass(decl)
            }
        }
    }

    private fun processClass(klass: IrClass) {
        if (klass.isEffectivelyExternal() || klass.isExpect) return

        val functions =
            klass.declarations
                .filterIsInstance<IrSimpleFunction>()
                .filter {
                    !it.isFakeOverride &&
                        it.dispatchReceiverParameter != null &&
                        it.modality != Modality.ABSTRACT &&
                        it.body != null
                }

        val fakeOverrides =
            klass.declarations
                .filterIsInstance<IrSimpleFunction>()
                .filter {
                    it.isFakeOverride &&
                        it.dispatchReceiverParameter != null &&
                        it.modality != Modality.ABSTRACT
                }

        val existingSignatures =
            klass.declarations
                .filterIsInstance<IrSimpleFunction>()
                .filter { !it.isFakeOverride && it.dispatchReceiverParameter != null }
                .mapTo(hashSetOf()) { luaFunctionSignature(it) }

        for (fn in functions) {
            val fnSig = luaFunctionSignature(fn)
            for (overridden in fn.allOverridden()) {
                val overriddenSig = luaFunctionSignature(overridden)
                if (overriddenSig != fnSig && existingSignatures.add(overriddenSig)) {
                    createBridge(klass, fn, overridden)
                }
            }
        }

        for (fake in fakeOverrides) {
            val target = fake.realOverrideTarget
            if (target.isFakeOverride || target.modality == Modality.ABSTRACT || target.dispatchReceiverParameter == null) {
                continue
            }
            val targetSig = luaFunctionSignature(target)
            for (overridden in fake.allOverridden()) {
                val overriddenSig = luaFunctionSignature(overridden)
                if (overriddenSig != targetSig && existingSignatures.add(overriddenSig)) {
                    createBridge(klass, target, overridden)
                }
            }
        }

        for (nested in klass.declarations.filterIsInstance<IrClass>()) {
            processClass(nested)
        }
    }

    private fun createBridge(
        klass: IrClass,
        target: IrSimpleFunction,
        overridden: IrSimpleFunction,
    ): IrSimpleFunction {
        val bridge =
            klass.addFunction {
                startOffset = target.startOffset
                endOffset = target.endOffset
                origin = IrDeclarationOrigin.BRIDGE
                name = overridden.name
                modality = Modality.OPEN
                visibility = overridden.visibility
                isSuspend = overridden.isSuspend
            }
        bridge.overriddenSymbols = listOf(overridden.symbol)
        bridge.copyTypeParametersFrom(overridden)
        bridge.returnType = overridden.returnType.remapTypeParameters(overridden, bridge)

        val dispatchParam = klass.thisReceiver!!.copyTo(bridge, type = klass.defaultType)
        bridge.parameters =
            listOf(dispatchParam) +
            overridden.nonDispatchParameters.map { param ->
                param.copyTo(bridge, defaultValue = null)
            }

        val call =
            IrCallImpl(
                startOffset = UNDEFINED_OFFSET,
                endOffset = UNDEFINED_OFFSET,
                type = target.returnType,
                symbol = target.symbol,
                typeArgumentsCount = target.typeParameters.size,
            ).apply {
                if (target.typeParameters.isNotEmpty() && bridge.typeParameters.size == target.typeParameters.size) {
                    passTypeArgumentsFrom(bridge)
                }
                arguments[0] = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, dispatchParam.type, dispatchParam.symbol)
                bridge.nonDispatchParameters.forEachIndexed { idx, param ->
                    val targetParam = target.nonDispatchParameters[idx]
                    val argGet = IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, param.type, param.symbol)
                    arguments[idx + 1] = argGet.implicitCastIfNeededTo(targetParam.type)
                }
            }

        val bodyStatements =
            if (bridge.returnType.isUnit()) {
                listOf(
                    call,
                    IrReturnImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        irBuiltIns.nothingType,
                        bridge.symbol,
                        IrGetObjectValueImpl(
                            UNDEFINED_OFFSET,
                            UNDEFINED_OFFSET,
                            irBuiltIns.unitType,
                            irBuiltIns.unitClass,
                        ),
                    ),
                )
            } else {
                listOf(
                    IrReturnImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        irBuiltIns.nothingType,
                        bridge.symbol,
                        call.implicitCastIfNeededTo(bridge.returnType),
                    ),
                )
            }

        bridge.body =
            context.irFactory.createBlockBody(target.startOffset, target.endOffset).apply {
                statements += bodyStatements
            }
        return bridge
    }
}
