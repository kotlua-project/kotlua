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

import io.github.kotlua.name.LuaStandardClassIds
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
    override val stringBuilder: IrClassSymbol by lazy {
        LuaClassIds.StringBuilder.classSymbol()
    }

    override val throwNullPointerException: IrSimpleFunctionSymbol by CallableIds.throwNpe.functionSymbol()

    override val throwTypeCastException: IrSimpleFunctionSymbol by CallableIds.throwCce.functionSymbol()

    override val throwKotlinNothingValueException: IrSimpleFunctionSymbol by CallableIds.throwKotlinNothingValueException.functionSymbol()

    override val throwUninitializedPropertyAccessException: IrSimpleFunctionSymbol by CallableIds.throwUninitializedPropertyAccessException
        .functionSymbol()

    override val throwUnsupportedOperationException: IrSimpleFunctionSymbol by CallableIds.throwUnsupportedOperationException
        .functionSymbol()

    override val defaultConstructorMarker: IrClassSymbol by lazy {
        LuaClassIds.DefaultConstructorMarker.classSymbol()
    }

    override val syntheticConstructorMarker: IrClassSymbol by lazy {
        LuaClassIds.DefaultConstructorMarker.classSymbol()
    }

    override val functionAdapter: IrClassSymbol by lazy {
        LuaClassIds.FunctionAdapter.classSymbol()
    }

//    val enumEntries: IrClassSymbol = StandardClassIds.EnumEntries.classSymbol()

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

    override val coroutineSuspendedGetter: IrSimpleFunctionSymbol by CallableIds.coroutineSuspended.getterSymbol()

    override val getContinuation: IrSimpleFunctionSymbol by CallableIds.getContinuation.functionSymbol()

    override val returnIfSuspended: IrSimpleFunctionSymbol by CallableIds.returnIfSuspended.functionSymbol()

    val luaEq: IrSimpleFunctionSymbol by LuaCallableIds.luaEq.functionSymbol()
    val luaNeq: IrSimpleFunctionSymbol by LuaCallableIds.luaNeq.functionSymbol()
    val luaGt: IrSimpleFunctionSymbol by LuaCallableIds.luaGt.functionSymbol()
    val luaGte: IrSimpleFunctionSymbol by LuaCallableIds.luaGte.functionSymbol()
    val luaLt: IrSimpleFunctionSymbol by LuaCallableIds.luaLt.functionSymbol()
    val luaLte: IrSimpleFunctionSymbol by LuaCallableIds.luaLte.functionSymbol()

    val luaNot: IrSimpleFunctionSymbol by LuaCallableIds.luaNot.functionSymbol()
    val luaAnd: IrSimpleFunctionSymbol by LuaCallableIds.luaAnd.functionSymbol()
    val luaOr: IrSimpleFunctionSymbol by LuaCallableIds.luaOr.functionSymbol()
    val luaUnaryMinus: IrSimpleFunctionSymbol by LuaCallableIds.luaUnaryMinus.functionSymbol()

    val luaPlus: IrSimpleFunctionSymbol by LuaCallableIds.luaPlus.functionSymbol()
    val luaMinus: IrSimpleFunctionSymbol by LuaCallableIds.luaMinus.functionSymbol()
    val luaMult: IrSimpleFunctionSymbol by LuaCallableIds.luaMult.functionSymbol()
    val luaDiv: IrSimpleFunctionSymbol by LuaCallableIds.luaDiv.functionSymbol()
    val luaMod: IrSimpleFunctionSymbol by LuaCallableIds.luaMod.functionSymbol()
    val luaPow: IrSimpleFunctionSymbol by LuaCallableIds.luaPow.functionSymbol()

    val luaConcat: IrSimpleFunctionSymbol by LuaCallableIds.luaConcat.functionSymbol()
    val luaLength: IrSimpleFunctionSymbol by LuaCallableIds.luaLength.functionSymbol()

    val luaEmptyTable: IrSimpleFunctionSymbol by LuaCallableIds.luaEmptyTable.functionSymbol()
    val luaTableGet: IrSimpleFunctionSymbol by LuaCallableIds.luaTableGet.functionSymbol()
    val luaTableSet: IrSimpleFunctionSymbol by LuaCallableIds.luaTableSet.functionSymbol()
    val unreachable: IrSimpleFunctionSymbol by LuaCallableIds.unreachable.functionSymbol()

    override val luaCode: IrSimpleFunctionSymbol by LuaStandardClassIds.Callables.LuaCode.functionSymbol()

    override fun isSideEffectFree(call: IrCall): Boolean = call.symbol == luaEmptyTable || super.isSideEffectFree(call)
}

private object LuaClassIds {
    val BASE_LUA_PACKAGE = FqName("kotlin.lua")
    val BASE_LUA_INTERNAL_PACKAGE = FqName("kotlin.lua.internal")

    val StringBuilder = ClassId(StandardClassIds.BASE_TEXT_PACKAGE, Name.identifier("StringBuilder"))

    private val String.luaInternalClassId get() = ClassId(BASE_LUA_INTERNAL_PACKAGE, Name.identifier(this))

    val FunctionAdapter = "FunctionAdapter".luaInternalClassId
    val DefaultConstructorMarker = "DefaultConstructorMarker".luaInternalClassId

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
    private val String.luaCallableId get() = CallableId(LuaClassIds.BASE_LUA_PACKAGE, Name.identifier(this))

    val luaEq = "luaEq".luaCallableId
    val luaNeq = "luaNeq".luaCallableId
    val luaGt = "luaGt".luaCallableId
    val luaGte = "luaGte".luaCallableId
    val luaLt = "luaLt".luaCallableId
    val luaLte = "luaLte".luaCallableId

    val luaNot = "luaNot".luaCallableId
    val luaAnd = "luaAnd".luaCallableId
    val luaOr = "luaOr".luaCallableId
    val luaUnaryMinus = "luaUnaryMinus".luaCallableId

    val luaPlus = "luaPlus".luaCallableId
    val luaMinus = "luaMinus".luaCallableId
    val luaMult = "luaMult".luaCallableId
    val luaDiv = "luaDiv".luaCallableId
    val luaMod = "luaMod".luaCallableId
    val luaPow = "luaPow".luaCallableId

    val luaConcat = "luaConcat".luaCallableId
    val luaLength = "luaLength".luaCallableId

    val luaEmptyTable = "luaEmptyTable".luaCallableId
    val luaTableGet = "luaTableGet".luaCallableId
    val luaTableSet = "luaTableSet".luaCallableId
    val unreachable = "unreachable".luaCallableId
}
