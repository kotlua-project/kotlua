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

import io.github.kotlua.library.writer.LuaKlibManifestComponentWriter.Companion.NON_CUSTOMIZED_PROPERTY_NAMES
import org.jetbrains.kotlin.io.zipDirAs
import org.jetbrains.kotlin.library.KlibFormat
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.KotlinLibraryVersioning
import org.jetbrains.kotlin.library.impl.BuiltInsPlatform
import org.jetbrains.kotlin.library.writer.KlibComponentWriter
import org.jetbrains.kotlin.library.writer.KlibManifestWriterSpec
import org.jetbrains.kotlin.library.writer.KlibWriterSpec
import java.nio.file.Files.createTempDirectory
import java.nio.file.Path
import java.util.*
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

/**
 * The [LuaKlibWriter] component is the unified endpoint for writing [KotlinLibrary] artifacts to the file system.
 *
 * It can be configured using the [KlibWriterSpec] and [KlibManifestWriterSpec] DSLs.
 *
 * The component performs a limited set of basic checks, such as
 * - Checking that all the required manifest properties have been specified: module name, versions
 * - Checking validity of the supplied module name
 */
class LuaKlibWriter(
    init: KlibWriterSpec.() -> Unit,
) {
    private var format: KlibFormat = KlibFormat.Directory
    private var allowIncrementalOverwriting: Boolean = false
    private val componentWriters = mutableListOf<KlibComponentWriter>()

    private var moduleName: String? = null
    private var versions: KotlinLibraryVersioning? = null
    private val customProperties = Properties()

    init {
        object : KlibWriterSpec, KlibManifestWriterSpec {
            /*** [KlibWriterSpec] ***/

            override fun format(format: KlibFormat) {
                this@LuaKlibWriter.format = format
            }

            override fun allowIncrementalOverwriting(allowed: Boolean) {
                this@LuaKlibWriter.allowIncrementalOverwriting = allowed
            }

            override fun include(vararg writers: KlibComponentWriter) {
                this@LuaKlibWriter.componentWriters += writers
            }

            override fun include(writers: Collection<KlibComponentWriter>) {
                this@LuaKlibWriter.componentWriters += writers
            }

            override fun manifest(init: KlibManifestWriterSpec.() -> Unit) = init()

            /*** [KlibManifestWriterSpec] ***/

            override fun moduleName(moduleName: String) {
                this@LuaKlibWriter.moduleName = moduleName
            }

            override fun versions(versions: KotlinLibraryVersioning) {
                this@LuaKlibWriter.versions = versions
            }

            override fun platformAndTargets(
                builtInsPlatform: BuiltInsPlatform,
                targetNames: Collection<String>,
            ) {
                error("Should not be called")
            }

            override fun platformAndTargets(
                builtInsPlatform: BuiltInsPlatform,
                vararg targetNames: String,
            ) = platformAndTargets(builtInsPlatform, targetNames.asList())

            override fun customProperties(init: Properties.() -> Unit) {
                customProperties.init()
            }
        }.init()
    }

    @OptIn(ExperimentalPathApi::class)
    fun writeTo(destinationPath: Path) {
        check(!allowIncrementalOverwriting || format != KlibFormat.ZipArchive) {
            "Writing a KLIB in ZIP format if `allowIncrementalOverwriting` is set to `true` is not supported."
        }

        val allComponentWriters: List<KlibComponentWriter> =
            buildList {
                this += componentWriters

                // Note: Calling this function may throw an exception if manifest is misconfigured.
                this += validateManifestPropertiesAndCreateComponentWriter()

                // This is the workaround for the 'resources' directory.
                this += KlibResourcesComponentWriterImpl
            }

        when {
            !destinationPath.exists() -> {
                // We need to compute absolute path for the generated KLIB artifact
                // in order to be able to create all containing directories beforehand.
                //
                // Illustrative example:
                //   Path(".").parent == null
                //   Path(".").absolute().parent != null
                destinationPath.absolute().parent.createDirectories()
            }

            !allowIncrementalOverwriting -> {
                destinationPath.deleteRecursively()
            }
        }

        when (format) {
            KlibFormat.Directory -> {
                writeComponents(allComponentWriters, root = destinationPath)
            }

            KlibFormat.ZipArchive -> {
                val temporaryDir = createTempDirectory("klib")
                writeComponents(allComponentWriters, root = temporaryDir)
                temporaryDir.zipDirAs(destinationPath)
                temporaryDir.deleteRecursively()
            }
        }
    }

    fun writeTo(destinationPath: String) = writeTo(Path(destinationPath))

    private fun validateManifestPropertiesAndCreateComponentWriter(): LuaKlibManifestComponentWriter {
        val moduleName =
            checkNotNull(moduleName) {
                "Module name is not specified. Use `KlibWriter { manifest { moduleName(...) } }` to set it."
            }

        check(moduleName.isNotBlank()) { "Module name cannot be empty." }

        val versions =
            checkNotNull(versions) {
                "Klib versions are not specified. Use `KlibWriter { manifest { versions(...) } }` to set them."
            }

        val nonCustomizedPropertyNames = customProperties.stringPropertyNames() intersect NON_CUSTOMIZED_PROPERTY_NAMES
        check(nonCustomizedPropertyNames.isEmpty()) {
            "Custom properties $nonCustomizedPropertyNames are not allowed to be " +
                "added through `KlibWriter { manifest { customProperties(...) } }`. " +
                "Use other functions to set them."
        }

        return LuaKlibManifestComponentWriter(
            moduleName = moduleName,
            versions = versions,
            customProperties = customProperties,
        )
    }

    companion object {
        private fun writeComponents(
            allComponentWriters: List<KlibComponentWriter>,
            root: Path,
        ) {
            for (componentWriter in allComponentWriters) {
                componentWriter.writeTo(root)
            }
        }
    }
}
