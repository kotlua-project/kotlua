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
package io.github.kotlua.library

import org.jetbrains.kotlin.library.BaseKotlinLibrary
import org.jetbrains.kotlin.library.KLIB_PROPERTY_BUILTINS_PLATFORM
import org.jetbrains.kotlin.library.uniqueName

const val LUA_PLATFORM_NAME: String = "LUA"
const val KOTLIN_LUA_STDLIB_NAME: String = "kotlin"

val BaseKotlinLibrary.isLuaPlatform: Boolean
    get() = manifestProperties.getProperty(KLIB_PROPERTY_BUILTINS_PLATFORM) == LUA_PLATFORM_NAME

val BaseKotlinLibrary.isLuaStdlib: Boolean
    get() = uniqueName == KOTLIN_LUA_STDLIB_NAME && isLuaPlatform
