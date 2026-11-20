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
package io.github.kotlua.name

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds.BASE_KOTLIN_PACKAGE

object LuaStandardClassIds {
    val BASE_LUA_PACKAGE = BASE_KOTLIN_PACKAGE.child(Name.identifier("lua"))
    val BASE_LUA_INTERNAL_PACKAGE = BASE_LUA_PACKAGE.child(Name.identifier("internal"))

    object Annotations {
        @JvmField
        val LuaOutlinedFunction = "LuaOutlinedFunction".luaId()

        @JvmField
        val DoNotIntrinsify = "DoNotIntrinsify".luaId()

        @JvmField
        val LuaIntrinsic = "LuaIntrinsic".luaId()

        @JvmField
        val LuaMultiReturn = "LuaMultiReturn".luaId()
    }

    object Callables {
        @JvmField
        val LuaCode = "lua".callableId(BASE_LUA_PACKAGE)
    }
}

private fun String.luaId() = ClassId(LuaStandardClassIds.BASE_LUA_PACKAGE, Name.identifier(this))

private fun String.id() = ClassId(BASE_KOTLIN_PACKAGE, Name.identifier(this))

private fun String.callableId(packageName: FqName) = CallableId(packageName, Name.identifier(this))
