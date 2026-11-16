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
@file:Suppress("NON_MEMBER_FUNCTION_NO_BODY", "UNUSED_PARAMETER", "unused")

package kotlin.lua

import kotlin.internal.UsedFromCompilerGeneratedCode

@RequiresOptIn(message = "This is a compiler intrinsic for Kotlua, proceed with care!")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
internal annotation class LuaIntrinsic

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
internal annotation class DoNotIntrinsify

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaEq(a: Any?, b: Any?): Boolean

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaNeq(a: Any?, b: Any?): Boolean

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaGt(a: Any?, b: Any?): Boolean

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaGte(a: Any?, b: Any?): Boolean

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaLt(a: Any?, b: Any?): Boolean

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaLte(a: Any?, b: Any?): Boolean

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaNot(a: Any?): Boolean

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaAnd(a: Any?, b: Any?): Any?

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaOr(a: Any?, b: Any?): Any?

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaPlus(a: Any?, b: Any?): Any?

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaMinus(a: Any?, b: Any?): Any?

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaMult(a: Any?, b: Any?): Any?

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaDiv(a: Any?, b: Any?): Any?

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaMod(a: Any?, b: Any?): Any?

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaPow(a: Any?, b: Any?): Any?

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaUnaryMinus(a: Any?): Any?

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaConcat(a: Any?, b: Any?): String

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaLength(a: Any?): Int

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaEmptyTable(): Any?

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaTableGet(table: Any?, key: Any?): Any?

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun luaTableSet(table: Any?, key: Any?, value: Any?)

@LuaIntrinsic
@UsedFromCompilerGeneratedCode
internal fun unreachable(): Nothing