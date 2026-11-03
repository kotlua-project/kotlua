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
package io.github.kotlua.ir

import io.github.kotlua.name.BASE_LUA_PACKAGE
import org.jetbrains.kotlin.backend.common.ir.PreSerializationKlibSymbols
import org.jetbrains.kotlin.builtins.StandardNames.COROUTINES_PACKAGE_FQ_NAME
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.functionSymbol
import org.jetbrains.kotlin.ir.getterSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

interface PreSerializationLuaSymbols : PreSerializationKlibSymbols {
    open class Impl(
        irBuiltIns: IrBuiltIns,
    ) : PreSerializationKlibSymbols.Impl(irBuiltIns),
        PreSerializationLuaSymbols {
        override val coroutineContextGetter: IrSimpleFunctionSymbol by CallableIds.coroutineContextGetter.getterSymbol()
        override val coroutineGetContext: IrSimpleFunctionSymbol by CallableIds.coroutineGetContext.functionSymbol()
        override val suspendCoroutineUninterceptedOrReturn: IrSimpleFunctionSymbol by CallableIds.suspendCoroutineUninterceptedOrReturn.functionSymbol()

        companion object {
            private const val COROUTINE_SUSPEND_OR_RETURN_LUA_NAME = "suspendCoroutineUninterceptedOrReturnLua"

            private object CallableIds {
                private val String.baseLuaCallableId: CallableId
                    get() = CallableId(StandardClassIds.BASE_LUA_PACKAGE, Name.identifier(this))
                val coroutineContextGetter: CallableId =
                    CallableId(COROUTINES_PACKAGE_FQ_NAME, Name.identifier(PreSerializationKlibSymbols.COROUTINE_CONTEXT_NAME))

                val coroutineGetContext: CallableId = PreSerializationKlibSymbols.GET_COROUTINE_CONTEXT_NAME.baseLuaCallableId
                val suspendCoroutineUninterceptedOrReturn: CallableId = COROUTINE_SUSPEND_OR_RETURN_LUA_NAME.baseLuaCallableId
            }

            private object ClassIds
        }
    }
}
