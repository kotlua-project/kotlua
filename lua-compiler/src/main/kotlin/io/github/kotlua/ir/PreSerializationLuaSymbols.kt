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

import org.jetbrains.kotlin.backend.common.ir.PreSerializationKlibSymbols
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol

interface PreSerializationLuaSymbols : PreSerializationKlibSymbols {
    open class Impl(
        irBuiltIns: IrBuiltIns,
    ) : PreSerializationKlibSymbols.Impl(irBuiltIns),
        PreSerializationLuaSymbols {
        override val coroutineContextGetter: IrSimpleFunctionSymbol
            get() = TODO("Not yet implemented")
        override val coroutineGetContext: IrSimpleFunctionSymbol
            get() = TODO("Not yet implemented")
        override val suspendCoroutineUninterceptedOrReturn: IrSimpleFunctionSymbol
            get() = TODO("Not yet implemented")

        companion object {
            private object CallableIds

            private object ClassIds
        }
    }
}
