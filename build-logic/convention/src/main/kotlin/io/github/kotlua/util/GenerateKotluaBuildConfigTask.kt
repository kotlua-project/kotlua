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
package io.github.kotlua.util

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateKotluaBuildConfigTask : DefaultTask() {
    @get:Input
    abstract val kotluaGroup: Property<String>

    @get:Input
    abstract val kotluaVersion: Property<String>

    @get:Input
    abstract val kotlinVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageDir = outputDir.get().asFile.resolve("io/github/kotlua/gradle")
        packageDir.mkdirs()
        packageDir.resolve("KotluaBuildConfig.kt").writeText(
            """
            package io.github.kotlua.gradle

            internal object KotluaBuildConfig {
                const val GROUP: String = "${kotluaGroup.get()}"
                const val VERSION: String = "${kotluaVersion.get()}"
                const val KOTLIN_VERSION: String = "${kotlinVersion.get()}"
                const val COMPILER_ARTIFACT: String = "kotlua-compiler"
                const val STDLIB_ARTIFACT: String = "kotlin-stdlib-lua"
            }
            """.trimIndent() + "\n",
        )
    }
}
