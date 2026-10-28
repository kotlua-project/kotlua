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

import org.jetbrains.kotlin.backend.common.ir.BackendKlibSymbols
import org.jetbrains.kotlin.backend.common.ir.PreSerializationKlibSymbols
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.classSymbol
import org.jetbrains.kotlin.ir.declarations.StageController
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.functionSymbol
import org.jetbrains.kotlin.ir.getterSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.*

class BackendLuaSymbols(
    irBuiltIns: IrBuiltIns,
    private val stageController: StageController,
) : BackendKlibSymbols(irBuiltIns),
    PreSerializationLuaSymbols by PreSerializationLuaSymbols.Impl(irBuiltIns) {
    override val stringBuilder: IrClassSymbol = LuaClassIds.StringBuilder.classSymbol()

    override val throwNullPointerException: IrSimpleFunctionSymbol
        by CallableIds.throwNpe.functionSymbol()

    override val throwTypeCastException: IrSimpleFunctionSymbol
        by CallableIds.throwCce.functionSymbol()

    override val throwKotlinNothingValueException: IrSimpleFunctionSymbol
        by CallableIds.throwKotlinNothingValueException.functionSymbol()

    override val throwUninitializedPropertyAccessException: IrSimpleFunctionSymbol
        by CallableIds.throwUninitializedPropertyAccessException.functionSymbol()

    override val throwUnsupportedOperationException: IrSimpleFunctionSymbol
        by CallableIds.throwUnsupportedOperationException.functionSymbol()

    val noWhenBranchMatchedException: IrSimpleFunctionSymbol
        by CallableIds.noWhenBranchMatchedException.functionSymbol()

    val throwIse: IrSimpleFunctionSymbol
        by CallableIds.throwIse.functionSymbol()

    val throwIae: IrSimpleFunctionSymbol
        by CallableIds.throwIae.functionSymbol()

    override val defaultConstructorMarker: IrClassSymbol = LuaClassIds.DefaultConstructorMarker.classSymbol()

    override val syntheticConstructorMarker: IrClassSymbol = LuaClassIds.DefaultConstructorMarker.classSymbol()

    override val functionAdapter: IrClassSymbol = LuaClassIds.FunctionAdapter.classSymbol()

    val enumEntries: IrClassSymbol = StandardClassIds.EnumEntries.classSymbol()

    override val genericSharedVariableBox: PreSerializationKlibSymbols.SharedVariableBoxClassInfo by lazy {
//        val classSymbol = LuaClassIds.ObjectRef.classSymbol()
//        val primaryConstructor = classSymbol.owner.primaryConstructorSymbol()
//        val valueProperty = classSymbol.owner.propertySymbol(Name.identifier("element"))
//        PreSerializationKlibSymbols.SharedVariableBoxClassInfo(
//            classSymbol,
//            primaryConstructor,
//            valueProperty
//        )
        TODO()
    }

    override val continuationClass: IrClassSymbol = LuaClassIds.Continuation.classSymbol()

    override val coroutineSuspendedGetter: IrSimpleFunctionSymbol
        by CallableIds.coroutineSuspended.getterSymbol()

    override val getContinuation: IrSimpleFunctionSymbol
        by CallableIds.getContinuation.functionSymbol()

    override val returnIfSuspended: IrSimpleFunctionSymbol
        by CallableIds.returnIfSuspended.functionSymbol()

    val luaSetMetatable: IrSimpleFunctionSymbol by LuaCallableIds.setMetatable.functionSymbol()
    val luaGetMetatable: IrSimpleFunctionSymbol by LuaCallableIds.getMetatable.functionSymbol()
    val luaRawGet: IrSimpleFunctionSymbol by LuaCallableIds.rawGet.functionSymbol()
    val luaRawSet: IrSimpleFunctionSymbol by LuaCallableIds.rawSet.functionSymbol()
    val luaTableNew: IrSimpleFunctionSymbol by LuaCallableIds.tableNew.functionSymbol()
    val luaTypeOf: IrSimpleFunctionSymbol by LuaCallableIds.typeOf.functionSymbol()
    val luaStringConcat: IrSimpleFunctionSymbol by LuaCallableIds.stringConcat.functionSymbol()
    val luaLengthOp: IrSimpleFunctionSymbol by LuaCallableIds.lengthOp.functionSymbol()
    val luaError: IrSimpleFunctionSymbol by LuaCallableIds.error.functionSymbol()
    val luaPCall: IrSimpleFunctionSymbol by LuaCallableIds.pcall.functionSymbol()
    val luaCode: IrSimpleFunctionSymbol by LuaCallableIds.lua.functionSymbol()

    override fun isSideEffectFree(call: IrCall): Boolean =
        call.symbol == luaRawGet ||
            call.symbol == luaTypeOf ||
            call.symbol == luaLengthOp ||
            super.isSideEffectFree(call)
}

private object LuaClassIds {
    val BASE_LUA_PACKAGE = FqName("kotlin.lua")
    val BASE_LUA_INTERNAL_PACKAGE = FqName("kotlin.lua.internal")

    val StringBuilder = ClassId(StandardClassIds.BASE_TEXT_PACKAGE, Name.identifier("StringBuilder"))

    private val String.luaInternalClassId get() = ClassId(BASE_LUA_INTERNAL_PACKAGE, Name.identifier(this))

    val FunctionAdapter = "FunctionAdapter".luaInternalClassId
    val DefaultConstructorMarker = "DefaultConstructorMarker".luaInternalClassId

    val ObjectRef = ClassId(FqName("kotlin.jvm.internal.Ref"), Name.identifier("ObjectRef"))

    val Continuation = ClassId(StandardClassIds.BASE_COROUTINES_PACKAGE, Name.identifier("Continuation"))
}

private object CallableIds {
    private val String.internalCallableId get() = CallableId(StandardClassIds.BASE_INTERNAL_PACKAGE, Name.identifier(this))
    private val String.kotlinCallableId get() = CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier(this))
    private val String.coroutinesIntrinsicsId get() = CallableId(StandardClassIds.BASE_COROUTINES_INTRINSICS_PACKAGE, Name.identifier(this))

    val throwNpe = "THROW_NPE".kotlinCallableId
    val throwCce = "THROW_CCE".kotlinCallableId
    val throwKotlinNothingValueException = "throwKotlinNothingValueException".kotlinCallableId
    val noWhenBranchMatchedException = "noWhenBranchMatchedException".kotlinCallableId
    val throwIse = "THROW_ISE".kotlinCallableId
    val throwIae = "THROW_IAE".kotlinCallableId

    val throwUninitializedPropertyAccessException = "throwUninitializedPropertyAccessException".internalCallableId
    val throwUnsupportedOperationException = "throwUnsupportedOperationException".kotlinCallableId

    val coroutineSuspended = "COROUTINE_SUSPENDED".coroutinesIntrinsicsId
    val getContinuation = "getContinuation".coroutinesIntrinsicsId
    val returnIfSuspended = "returnIfSuspended".coroutinesIntrinsicsId
    val suspendCoroutineUninterceptedOrReturn = "suspendCoroutineUninterceptedOrReturn".coroutinesIntrinsicsId
    val coroutineContext = "coroutineContext".coroutinesIntrinsicsId
    val coroutineGetContext = "coroutineGetContext".coroutinesIntrinsicsId
}

private object LuaCallableIds {
    private val String.luaCallableId get() = CallableId(LuaClassIds.BASE_LUA_INTERNAL_PACKAGE, Name.identifier(this))

    val setMetatable = "setmetatable".luaCallableId
    val getMetatable = "getmetatable".luaCallableId
    val rawGet = "rawget".luaCallableId
    val rawSet = "rawset".luaCallableId
    val tableNew = "tableNew".luaCallableId
    val typeOf = "typeOf".luaCallableId
    val stringConcat = "stringConcat".luaCallableId
    val lengthOp = "lengthOp".luaCallableId
    val error = "error".luaCallableId
    val pcall = "pcall".luaCallableId
    val lua = CallableId(LuaClassIds.BASE_LUA_PACKAGE, Name.identifier("lua"))
}
