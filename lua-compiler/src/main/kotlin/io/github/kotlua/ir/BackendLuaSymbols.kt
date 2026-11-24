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
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.classSymbol
import org.jetbrains.kotlin.ir.declarations.StageController
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.functionSymbol
import org.jetbrains.kotlin.ir.functionSymbolAssociatedBy
import org.jetbrains.kotlin.ir.getterSymbol
import org.jetbrains.kotlin.ir.setterSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.hasShape
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

    val throwIllegalArgumentException: IrSimpleFunctionSymbol by CallableIds.throwIae.functionSymbol()

    override val defaultConstructorMarker: IrClassSymbol = LuaClassIds.DefaultConstructorMarker.classSymbol()

    override val functionAdapter: IrClassSymbol by lazy {
        LuaClassIds.FunctionAdapter.classSymbol()
    }

    val enumEntries: IrClassSymbol = LuaClassIds.EnumEntries.classSymbol()

    val createEnumEntries: IrSimpleFunctionSymbol by CallableIds.createEnumEntries.functionSymbol()

    val enumEntriesIntrinsic: IrSimpleFunctionSymbol by CallableIds.enumEntriesIntrinsic.functionSymbol()

    override val continuationClass: IrClassSymbol = LuaClassIds.Continuation.classSymbol()

    override val coroutineSuspendedGetter: IrSimpleFunctionSymbol by CallableIds.coroutineSuspended.getterSymbol()

    override val getContinuation: IrSimpleFunctionSymbol by LuaCallableIds.getContinuation.functionSymbol()

    override val returnIfSuspended: IrSimpleFunctionSymbol by LuaCallableIds.returnIfSuspended.functionSymbol()

    val coroutineImpl: IrClassSymbol = LuaClassIds.CoroutineImpl.classSymbol()

    val coroutineImplLabelPropertyGetter by CallableIds.coroutineState.getterSymbol()
    val coroutineImplLabelPropertySetter by CallableIds.coroutineState.setterSymbol()
    val coroutineImplResultSymbolGetter by CallableIds.coroutineResult.getterSymbol()
    val coroutineImplExceptionPropertyGetter by CallableIds.coroutineException.getterSymbol()
    val coroutineImplExceptionPropertySetter by CallableIds.coroutineException.setterSymbol()
    val coroutineImplExceptionStatePropertyGetter by CallableIds.coroutineExceptionState.getterSymbol()
    val coroutineImplExceptionStatePropertySetter by CallableIds.coroutineExceptionState.setterSymbol()

    val luaMakeMainContinuation by CallableIds.makeMainContinuation.functionSymbol()

    val primitiveCompanionObjects =
        primitivesWithImplicitCompanionObject().associateWith {
            ClassId(LuaStandardClassIds.BASE_LUA_INTERNAL_PACKAGE, Name.identifier("${it.identifier}CompanionObject")).classSymbol()
        }

    private fun primitivesWithImplicitCompanionObject(): List<Name> {
        val numbers =
            PrimitiveType.NUMBER_TYPES
                .filter { it.name != "LONG" && it.name != "CHAR" } // skip due to they have own explicit companions
                .map { it.typeName }

        return numbers + listOf(Name.identifier("String"), Name.identifier("Boolean"))
    }

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

    val luaEquals: IrSimpleFunctionSymbol by LuaCallableIds.equals.functionSymbol()
    val luaToString: IrSimpleFunctionSymbol by LuaCallableIds.toString.functionSymbol()
    val luaIntDiv: IrSimpleFunctionSymbol by LuaCallableIds.intDiv.functionSymbol()
    val luaRem: IrSimpleFunctionSymbol by LuaCallableIds.rem.functionSymbol()

    val ensureNotNull: IrSimpleFunctionSymbol by CallableIds.ensureNotNull.functionSymbol()

    val luaBitAnd: IrSimpleFunctionSymbol by LuaCallableIds.bitAnd.functionSymbol()
    val luaBitOr: IrSimpleFunctionSymbol by LuaCallableIds.bitOr.functionSymbol()
    val luaBitXor: IrSimpleFunctionSymbol by LuaCallableIds.bitXor.functionSymbol()
    val luaBitNot: IrSimpleFunctionSymbol by LuaCallableIds.bitNot.functionSymbol()
    val luaBitShl: IrSimpleFunctionSymbol by LuaCallableIds.bitShl.functionSymbol()
    val luaBitShr: IrSimpleFunctionSymbol by LuaCallableIds.bitShr.functionSymbol()
    val luaBitUshr: IrSimpleFunctionSymbol by LuaCallableIds.bitUshr.functionSymbol()

    val luaDefineClass: IrSimpleFunctionSymbol by LuaCallableIds.defineClass.functionSymbol()

    val luaAnyToString: IrSimpleFunctionSymbol by LuaCallableIds.anyToString.functionSymbol()
    val luaHashCode: IrSimpleFunctionSymbol by LuaCallableIds.hashCode.functionSymbol()
    val luaAnyHashCode: IrSimpleFunctionSymbol by LuaCallableIds.anyHashCode.functionSymbol()

    val luaTypeOf: IrSimpleFunctionSymbol by LuaCallableIds.luaTypeOf.functionSymbol()
    val luaIsTypeOf: IrSimpleFunctionSymbol by LuaCallableIds.isTypeOf.functionSymbol()
    val luaGetMetatable: IrSimpleFunctionSymbol by LuaCallableIds.luaGetMetatable.functionSymbol()
    val luaSetMetatable: IrSimpleFunctionSymbol by LuaCallableIds.luaSetMetatable.functionSymbol()
    val luaClass: IrSimpleFunctionSymbol by LuaCallableIds.luaClass.functionSymbol()

    val luaIsInstance: IrSimpleFunctionSymbol by LuaCallableIds.isInstance.functionSymbol()
    val luaIsInt: IrSimpleFunctionSymbol by LuaCallableIds.isInt.functionSymbol()
    val luaIsByte: IrSimpleFunctionSymbol by LuaCallableIds.isByte.functionSymbol()
    val luaIsShort: IrSimpleFunctionSymbol by LuaCallableIds.isShort.functionSymbol()

    val luaError: IrSimpleFunctionSymbol by LuaCallableIds.luaError.functionSymbol()
    val luaPCall: IrSimpleFunctionSymbol by LuaCallableIds.luaPCall.functionSymbol()
    val luaInternalError: IrClassSymbol by lazy {
        LuaClassIds.LuaInternalError.classSymbol()
    }

    val luaMultiReturn: IrSimpleFunctionSymbol by LuaCallableIds.luaMultiReturn.functionSymbol()
    val luaPackToTable: IrSimpleFunctionSymbol by LuaCallableIds.luaPackToTable.functionSymbol()

    val luaMainArgs: IrSimpleFunctionSymbol by LuaCallableIds.luaMainArgs.functionSymbol()
    val luaUnpack: IrSimpleFunctionSymbol by LuaCallableIds.luaUnpack.functionSymbol()

    val luaCompareTo: IrSimpleFunctionSymbol by LuaCallableIds.compareTo.functionSymbol()

    val luaStringGet: IrSimpleFunctionSymbol by LuaCallableIds.stringGet.functionSymbol()
    val luaStringSubSequence: IrSimpleFunctionSymbol by LuaCallableIds.stringSubSequence.functionSymbol()

    val luaNumberToInt: IrSimpleFunctionSymbol by LuaCallableIds.numberToInt.functionSymbol()
    val luaIntToByte: IrSimpleFunctionSymbol by LuaCallableIds.intToByte.functionSymbol()
    val luaIntToShort: IrSimpleFunctionSymbol by LuaCallableIds.intToShort.functionSymbol()
    val luaNumberToByte: IrSimpleFunctionSymbol by LuaCallableIds.numberToByte.functionSymbol()
    val luaNumberToShort: IrSimpleFunctionSymbol by LuaCallableIds.numberToShort.functionSymbol()

    val luaCharToInt: IrSimpleFunctionSymbol by LuaCallableIds.charToInt.functionSymbol()
    val luaCharToByte: IrSimpleFunctionSymbol by LuaCallableIds.charToByte.functionSymbol()
    val luaCharToShort: IrSimpleFunctionSymbol by LuaCallableIds.charToShort.functionSymbol()
    val luaIntToChar: IrSimpleFunctionSymbol by LuaCallableIds.intToChar.functionSymbol()
    val luaNumberToChar: IrSimpleFunctionSymbol by LuaCallableIds.numberToChar.functionSymbol()
    val luaCharPlusInt: IrSimpleFunctionSymbol by LuaCallableIds.charPlusInt.functionSymbol()
    val luaCharMinusChar: IrSimpleFunctionSymbol by LuaCallableIds.charMinusChar.functionSymbol()
    val luaCharMinusInt: IrSimpleFunctionSymbol by LuaCallableIds.charMinusInt.functionSymbol()
    val charSequenceClass: IrClassSymbol by lazy {
        LuaClassIds.CharSequence.classSymbol()
    }

    val luaCharSequenceLength: IrSimpleFunctionSymbol by LuaCallableIds.charSequenceLength.functionSymbol()
    val luaCharSequenceGet: IrSimpleFunctionSymbol by LuaCallableIds.charSequenceGet.functionSymbol()
    val luaCharSequenceSubSequence: IrSimpleFunctionSymbol by LuaCallableIds.charSequenceSubSequence.functionSymbol()

    val luaNumberRangeToNumber: IrSimpleFunctionSymbol by LuaCallableIds.numberRangeToNumber.functionSymbol()
    val luaCharRangeToChar: IrSimpleFunctionSymbol by LuaCallableIds.charRangeToChar.functionSymbol()

    val rangeUntilFunctions: Map<Pair<IrType, IrType>, IrSimpleFunctionSymbol> by CallableIds.until.functionSymbolAssociatedBy(
        condition = { it.hasShape(extensionReceiver = true, regularParameters = 1) },
        getKey = { it.parameters[0].type to it.parameters[1].type },
    )

    val luaBoolAnd: IrSimpleFunctionSymbol by LuaCallableIds.boolAnd.functionSymbol()
    val luaBoolOr: IrSimpleFunctionSymbol by LuaCallableIds.boolOr.functionSymbol()
    val luaBoolXor: IrSimpleFunctionSymbol by LuaCallableIds.boolXor.functionSymbol()

    val luaIsLong: IrSimpleFunctionSymbol by LuaCallableIds.isLong.functionSymbol()
    val luaLongFromTwoInts: IrSimpleFunctionSymbol by LuaCallableIds.longFromTwoInts.functionSymbol()
    val luaLongFromInt: IrSimpleFunctionSymbol by LuaCallableIds.longFromInt.functionSymbol()
    val luaCharToLong: IrSimpleFunctionSymbol by LuaCallableIds.charToLong.functionSymbol()
    val luaNumberToLong: IrSimpleFunctionSymbol by LuaCallableIds.numberToLong.functionSymbol()
    val luaLongToInt: IrSimpleFunctionSymbol by LuaCallableIds.longToInt.functionSymbol()
    val luaLongToByte: IrSimpleFunctionSymbol by LuaCallableIds.longToByte.functionSymbol()
    val luaLongToShort: IrSimpleFunctionSymbol by LuaCallableIds.longToShort.functionSymbol()
    val luaLongToChar: IrSimpleFunctionSymbol by LuaCallableIds.longToChar.functionSymbol()
    val luaLongToNumber: IrSimpleFunctionSymbol by LuaCallableIds.longToNumber.functionSymbol()

    val luaDynamicNumberToByte: IrSimpleFunctionSymbol by LuaCallableIds.dynamicNumberToByte.functionSymbol()
    val luaDynamicNumberToShort: IrSimpleFunctionSymbol by LuaCallableIds.dynamicNumberToShort.functionSymbol()
    val luaDynamicNumberToInt: IrSimpleFunctionSymbol by LuaCallableIds.dynamicNumberToInt.functionSymbol()
    val luaDynamicNumberToLong: IrSimpleFunctionSymbol by LuaCallableIds.dynamicNumberToLong.functionSymbol()
    val luaDynamicNumberToDouble: IrSimpleFunctionSymbol by LuaCallableIds.dynamicNumberToDouble.functionSymbol()
    val luaDynamicNumberToChar: IrSimpleFunctionSymbol by LuaCallableIds.dynamicNumberToChar.functionSymbol()

    val luaLongEquals: IrSimpleFunctionSymbol by LuaCallableIds.longEquals.functionSymbol()
    val luaLongCompare: IrSimpleFunctionSymbol by LuaCallableIds.longCompare.functionSymbol()
    val luaLongLt: IrSimpleFunctionSymbol by LuaCallableIds.longLt.functionSymbol()
    val luaLongLte: IrSimpleFunctionSymbol by LuaCallableIds.longLte.functionSymbol()
    val luaLongGt: IrSimpleFunctionSymbol by LuaCallableIds.longGt.functionSymbol()
    val luaLongGte: IrSimpleFunctionSymbol by LuaCallableIds.longGte.functionSymbol()

    val luaLongNegate: IrSimpleFunctionSymbol by LuaCallableIds.longNegate.functionSymbol()
    val luaLongInc: IrSimpleFunctionSymbol by LuaCallableIds.longInc.functionSymbol()
    val luaLongDec: IrSimpleFunctionSymbol by LuaCallableIds.longDec.functionSymbol()
    val luaLongAdd: IrSimpleFunctionSymbol by LuaCallableIds.longAdd.functionSymbol()
    val luaLongSubtract: IrSimpleFunctionSymbol by LuaCallableIds.longSubtract.functionSymbol()
    val luaLongMultiply: IrSimpleFunctionSymbol by LuaCallableIds.longMultiply.functionSymbol()
    val luaLongDivide: IrSimpleFunctionSymbol by LuaCallableIds.longDivide.functionSymbol()
    val luaLongModulo: IrSimpleFunctionSymbol by LuaCallableIds.longModulo.functionSymbol()
    val luaLongAnd: IrSimpleFunctionSymbol by LuaCallableIds.longAnd.functionSymbol()
    val luaLongOr: IrSimpleFunctionSymbol by LuaCallableIds.longOr.functionSymbol()
    val luaLongXor: IrSimpleFunctionSymbol by LuaCallableIds.longXor.functionSymbol()
    val luaLongInv: IrSimpleFunctionSymbol by LuaCallableIds.longInv.functionSymbol()
    val luaLongShiftLeft: IrSimpleFunctionSymbol by LuaCallableIds.longShiftLeft.functionSymbol()
    val luaLongShiftRight: IrSimpleFunctionSymbol by LuaCallableIds.longShiftRight.functionSymbol()
    val luaLongShiftRightUnsigned: IrSimpleFunctionSymbol by LuaCallableIds.longShiftRightUnsigned.functionSymbol()
    val luaLongRangeToLong: IrSimpleFunctionSymbol by LuaCallableIds.longRangeToLong.functionSymbol()

    override val luaCode: IrSimpleFunctionSymbol by LuaStandardClassIds.Callables.LuaCode.functionSymbol()

    override fun isSideEffectFree(call: IrCall): Boolean =
        call.symbol == luaEmptyTable ||
            call.symbol == luaLongFromTwoInts ||
            call.symbol == luaLongFromInt ||
            super.isSideEffectFree(call)
}

private object LuaClassIds {
    val StringBuilder = ClassId(StandardClassIds.BASE_TEXT_PACKAGE, Name.identifier("StringBuilder"))

    private val String.enumClassId get() = ClassId(StandardClassIds.BASE_ENUMS_PACKAGE, Name.identifier(this))
    private val String.luaInternalClassId get() = ClassId(LuaStandardClassIds.BASE_LUA_INTERNAL_PACKAGE, Name.identifier(this))
    private val String.luaClassId get() = ClassId(LuaStandardClassIds.BASE_LUA_PACKAGE, Name.identifier(this))

    val EnumEntries = "EnumEntries".enumClassId
    val FunctionAdapter = "FunctionAdapter".luaInternalClassId
    val DefaultConstructorMarker = "DefaultConstructorMarker".luaInternalClassId

    val LuaInternalError = "LuaInternalError".luaClassId

    val Continuation = ClassId(StandardClassIds.BASE_COROUTINES_PACKAGE, Name.identifier("Continuation"))
    val CharSequence = ClassId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("CharSequence"))
    val CoroutineImpl = ClassId(StandardClassIds.BASE_COROUTINES_PACKAGE, Name.identifier("CoroutineImpl"))
}

private object CallableIds {
    private val String.internalCallableId get() = CallableId(StandardClassIds.BASE_INTERNAL_PACKAGE, Name.identifier(this))
    private val String.kotlinCallableId get() = CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier(this))
    private val String.enumCallableId get() = CallableId(StandardClassIds.BASE_ENUMS_PACKAGE, Name.identifier(this))
    private val String.coroutinesIntrinsicsId get() = CallableId(StandardClassIds.BASE_COROUTINES_INTRINSICS_PACKAGE, Name.identifier(this))

    val ensureNotNull = "ensureNotNull".kotlinCallableId
    val throwNpe = "THROW_NPE".kotlinCallableId
    val throwCce = "THROW_CCE".kotlinCallableId
    val throwKotlinNothingValueException = "throwKotlinNothingValueException".kotlinCallableId
    val noWhenBranchMatchedException = "noWhenBranchMatchedException".kotlinCallableId
    val throwIse = "THROW_ISE".kotlinCallableId
    val throwIae = "THROW_IAE".kotlinCallableId

    val createEnumEntries = "createEnumEntries".enumCallableId
    val enumEntriesIntrinsic = "enumEntriesIntrinsic".enumCallableId

    val throwUninitializedPropertyAccessException = "throwUninitializedPropertyAccessException".internalCallableId
    val throwUnsupportedOperationException = "throwUnsupportedOperationException".kotlinCallableId

    val coroutineSuspended = "COROUTINE_SUSPENDED".coroutinesIntrinsicsId
    val suspendCoroutineUninterceptedOrReturn = "suspendCoroutineUninterceptedOrReturn".coroutinesIntrinsicsId
    val coroutineContext = "coroutineContext".coroutinesIntrinsicsId
    val coroutineGetContext = "coroutineGetContext".coroutinesIntrinsicsId

    val coroutineState = CallableId(Name.identifier("state")).withClassId(LuaClassIds.CoroutineImpl)
    val coroutineResult = CallableId(Name.identifier("result")).withClassId(LuaClassIds.CoroutineImpl)
    val coroutineException = CallableId(Name.identifier("exception")).withClassId(LuaClassIds.CoroutineImpl)
    val coroutineExceptionState = CallableId(Name.identifier("exceptionState")).withClassId(LuaClassIds.CoroutineImpl)
    val makeMainContinuation = CallableId(StandardClassIds.BASE_COROUTINES_PACKAGE, Name.identifier("makeMainContinuation"))

    val until = CallableId(StandardClassIds.BASE_RANGES_PACKAGE, Name.identifier("until"))
}

private object LuaCallableIds {
    private val String.luaCallableId get() = CallableId(LuaStandardClassIds.BASE_LUA_PACKAGE, Name.identifier(this))

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

    val equals = "equals".luaCallableId
    val intDiv = "intDiv".luaCallableId
    val rem = "rem".luaCallableId
    val toString = "toString".luaCallableId

    val bitAnd = "bitAnd".luaCallableId
    val bitOr = "bitOr".luaCallableId
    val bitXor = "bitXor".luaCallableId
    val bitNot = "bitNot".luaCallableId
    val bitShl = "bitShl".luaCallableId
    val bitShr = "bitShr".luaCallableId
    val bitUshr = "bitUshr".luaCallableId

    val defineClass = "defineClass".luaCallableId
    val anyToString = "anyToString".luaCallableId
    val hashCode = "hashCode".luaCallableId
    val anyHashCode = "anyHashCode".luaCallableId

    val luaTypeOf = "luaTypeOf".luaCallableId
    val isTypeOf = "isTypeOf".luaCallableId
    val luaGetMetatable = "luaGetMetatable".luaCallableId
    val luaSetMetatable = "luaSetMetatable".luaCallableId
    val luaClass = "luaClass".luaCallableId

    val isInstance = "isInstance".luaCallableId
    val isInt = "isInt".luaCallableId
    val isByte = "isByte".luaCallableId
    val isShort = "isShort".luaCallableId

    val luaError = "luaError".luaCallableId
    val luaPCall = "luaPCall".luaCallableId

    val luaMultiReturn = "luaMultiReturn".luaCallableId
    val luaPackToTable = "luaPackToTable".luaCallableId

    val luaMainArgs = "luaMainArgs".luaCallableId
    val luaUnpack = "luaUnpack".luaCallableId

    val compareTo = "compareTo".luaCallableId

    val stringGet = "stringGet".luaCallableId
    val stringSubSequence = "stringSubSequence".luaCallableId

    val numberToInt = "numberToInt".luaCallableId
    val intToByte = "intToByte".luaCallableId
    val intToShort = "intToShort".luaCallableId
    val numberToByte = "numberToByte".luaCallableId
    val numberToShort = "numberToShort".luaCallableId

    val charToInt = "charToInt".luaCallableId
    val charToByte = "charToByte".luaCallableId
    val charToShort = "charToShort".luaCallableId
    val intToChar = "intToChar".luaCallableId
    val numberToChar = "numberToChar".luaCallableId
    val charPlusInt = "charPlusInt".luaCallableId
    val charMinusChar = "charMinusChar".luaCallableId
    val charMinusInt = "charMinusInt".luaCallableId
    val charSequenceLength = "charSequenceLength".luaCallableId
    val charSequenceGet = "charSequenceGet".luaCallableId
    val charSequenceSubSequence = "charSequenceSubSequence".luaCallableId

    val numberRangeToNumber = "numberRangeToNumber".luaCallableId
    val charRangeToChar = "charRangeToChar".luaCallableId
    val boolAnd = "boolAnd".luaCallableId
    val boolOr = "boolOr".luaCallableId
    val boolXor = "boolXor".luaCallableId

    val isLong = "isLong".luaCallableId
    val longFromTwoInts = "longFromTwoInts".luaCallableId
    val longFromInt = "longFromInt".luaCallableId
    val charToLong = "charToLong".luaCallableId
    val numberToLong = "numberToLong".luaCallableId
    val longToInt = "longToInt".luaCallableId
    val longToByte = "longToByte".luaCallableId
    val longToShort = "longToShort".luaCallableId
    val longToChar = "longToChar".luaCallableId
    val longToNumber = "longToNumber".luaCallableId

    val dynamicNumberToByte = "dynamicNumberToByte".luaCallableId
    val dynamicNumberToShort = "dynamicNumberToShort".luaCallableId
    val dynamicNumberToInt = "dynamicNumberToInt".luaCallableId
    val dynamicNumberToLong = "dynamicNumberToLong".luaCallableId
    val dynamicNumberToDouble = "dynamicNumberToDouble".luaCallableId
    val dynamicNumberToChar = "dynamicNumberToChar".luaCallableId

    val longEquals = "longEquals".luaCallableId
    val longCompare = "longCompare".luaCallableId
    val longLt = "longLt".luaCallableId
    val longLte = "longLte".luaCallableId
    val longGt = "longGt".luaCallableId
    val longGte = "longGte".luaCallableId

    val longNegate = "longNegate".luaCallableId
    val longInc = "longInc".luaCallableId
    val longDec = "longDec".luaCallableId
    val longAdd = "longAdd".luaCallableId
    val longSubtract = "longSubtract".luaCallableId
    val longMultiply = "longMultiply".luaCallableId
    val longDivide = "longDivide".luaCallableId
    val longModulo = "longModulo".luaCallableId
    val longAnd = "longAnd".luaCallableId
    val longOr = "longOr".luaCallableId
    val longXor = "longXor".luaCallableId
    val longInv = "longInv".luaCallableId
    val longShiftLeft = "longShiftLeft".luaCallableId
    val longShiftRight = "longShiftRight".luaCallableId
    val longShiftRightUnsigned = "longShiftRightUnsigned".luaCallableId
    val longRangeToLong = "longRangeToLong".luaCallableId

    val getContinuation = "getContinuation".luaCallableId
    val returnIfSuspended = "returnIfSuspended".luaCallableId
}
