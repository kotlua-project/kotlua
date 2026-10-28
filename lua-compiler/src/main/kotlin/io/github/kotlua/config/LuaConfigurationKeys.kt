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
package io.github.kotlua.config

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import java.io.File

object LuaConfigurationKeys {
    @JvmField
    val INCLUDES = CompilerConfigurationKey.create<String>("INCLUDES")

    @JvmField
    val LIBRARIES = CompilerConfigurationKey.create<List<String>>("LIBRARIES")

    @JvmField
    val OUTPUT_DIR = CompilerConfigurationKey.create<File>("OUTPUT_DIR")

    @JvmField
    val OUTPUT_NAME = CompilerConfigurationKey.create<String>("OUTPUT_NAME")
}

var CompilerConfiguration.includes: String?
    get() = get(LuaConfigurationKeys.INCLUDES)
    set(value) {
        put(LuaConfigurationKeys.INCLUDES, requireNotNull(value) { "nullable values are not allowed" })
    }

var CompilerConfiguration.libraries: List<String>
    get() = getList(LuaConfigurationKeys.LIBRARIES)
    set(value) {
        put(LuaConfigurationKeys.LIBRARIES, value)
    }

var CompilerConfiguration.outputDir: File?
    get() = get(LuaConfigurationKeys.OUTPUT_DIR)
    set(value) {
        put(LuaConfigurationKeys.OUTPUT_DIR, requireNotNull(value) { "nullable values are not allowed" })
    }

var CompilerConfiguration.outputName: String?
    get() = get(LuaConfigurationKeys.OUTPUT_NAME)
    set(value) {
        put(LuaConfigurationKeys.OUTPUT_NAME, requireNotNull(value) { "nullable values are not allowed" })
    }
