/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Copyright 2026 The kotlua Authors.
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
 *
 * Notice: This file has been modified from its original form in the Kotlin project.
 */
package io.github.kotlua.library.writer

import io.github.kotlua.library.LUA_PLATFORM_NAME
import org.jetbrains.kotlin.io.writeProperties
import org.jetbrains.kotlin.library.KLIB_PROPERTY_ABI_VERSION
import org.jetbrains.kotlin.library.KLIB_PROPERTY_BUILTINS_PLATFORM
import org.jetbrains.kotlin.library.KLIB_PROPERTY_COMPILER_VERSION
import org.jetbrains.kotlin.library.KLIB_PROPERTY_IR_SIGNATURE_VERSIONS
import org.jetbrains.kotlin.library.KLIB_PROPERTY_METADATA_VERSION
import org.jetbrains.kotlin.library.KLIB_PROPERTY_UNIQUE_NAME
import org.jetbrains.kotlin.library.KlibConstants.KLIB_DEFAULT_COMPONENT_NAME
import org.jetbrains.kotlin.library.KlibConstants.KLIB_MANIFEST_FILE_NAME
import org.jetbrains.kotlin.library.KotlinLibraryVersioning
import org.jetbrains.kotlin.library.writeKonanLibraryVersioning
import org.jetbrains.kotlin.library.writer.KlibComponentWriter
import java.nio.file.Path
import java.util.Properties
import kotlin.collections.plusAssign
import kotlin.io.path.createDirectories

internal class LuaKlibManifestComponentWriter(
    val moduleName: String,
    val versions: KotlinLibraryVersioning,
    val customProperties: Properties,
) : KlibComponentWriter {
    override fun writeTo(root: Path) {
        val properties = assembleProperties()

        val manifestFile = root.resolve(KLIB_DEFAULT_COMPONENT_NAME).resolve(KLIB_MANIFEST_FILE_NAME)
        manifestFile.parent.createDirectories()
        manifestFile.writeProperties(properties)
    }

    private fun assembleProperties(): Properties =
        Properties().apply {
            this[KLIB_PROPERTY_UNIQUE_NAME] = moduleName
            this[KLIB_PROPERTY_BUILTINS_PLATFORM] = LUA_PLATFORM_NAME
            writeKonanLibraryVersioning(versions)
            this += customProperties
        }

    companion object {
        /**
         * The set of property names that cannot be specified through [KlibManifestWriterSpec.customProperties].
         */
        internal val NON_CUSTOMIZED_PROPERTY_NAMES =
            setOf(
                // moduleName
                KLIB_PROPERTY_UNIQUE_NAME,
                // versions
                KLIB_PROPERTY_ABI_VERSION,
                KLIB_PROPERTY_METADATA_VERSION,
                KLIB_PROPERTY_COMPILER_VERSION,
                KLIB_PROPERTY_IR_SIGNATURE_VERSIONS,
                // builtInsPlatform
                KLIB_PROPERTY_BUILTINS_PLATFORM,
            )
    }
}
