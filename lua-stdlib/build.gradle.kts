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
import io.github.kotlua.gradle.Kotlin2LuaCompile
import javax.inject.Inject
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    base
    alias(libs.plugins.kotlua.spotless)
    alias(libs.plugins.kotlua.gradlePlugin)
}

val compilerClasspath = configurations.create("compilerClasspath")

val commonSourcesConfig = configurations.create("commonSourcesConfig") {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType::class.java, DocsType.SOURCES))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.common)
    }
}

val unpackedCommonSourcesDir = layout.buildDirectory.dir("unpacked-common-sources")
val commonSourcesDir = unpackedCommonSourcesDir.map { it.dir("commonMain") }
val commonNonJvmMainDir = unpackedCommonSourcesDir.map { it.dir("commonNonJvmMain") }

dependencies {
    kotluaCompilerClasspath(projects.luaCompiler)
    commonSourcesConfig(libs.kotlin.stdlib)
}

tasks {
    withType<Kotlin2LuaCompile>().configureEach {
        commonSourceSet.from(commonSourcesDir.get())
        source("src/", "runtime/", "builtins/")
    }
}

kotlua {
    multiPlatformEnabled = true
    compilerOptions {
        moduleName = "kotlin-stdlib-lua"
        freeCompilerArgs.addAll(
            listOf(
                "-XXlenient-mode",
                "-Xallow-kotlin-package",
                "-Xstdlib-compilation",
                "-Xreturn-value-checker=full",
                "-Xexpect-actual-classes",
                "-Xname-based-destructuring=complete",
            ),
        )
        optIn = listOf(
            "kotlin.ExperimentalMultiplatform",
            "kotlin.contracts.ExperimentalContracts",
            "kotlin.ExperimentalStdlibApi",
            "kotlin.ExperimentalUnsignedTypes",
            "kotlin.uuid.ExperimentalUuidApi",
            "kotlin.time.ExperimentalTime",
            "kotlin.io.encoding.ExperimentalEncodingApi",
        )
    }
}

abstract class UnpackSourcesTask @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archiveFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val targetDir: DirectoryProperty

    @TaskAction
    fun unpack() {
        fileSystemOperations.sync {
            archiveFiles.forEach { file ->
                from(archiveOperations.zipTree(file)) {
                    include("commonMain/**", "commonNonJvmMain/**")
                    includeEmptyDirs = false
                }
            }
            into(targetDir)
        }
    }
}

val unpackCommonSources = tasks.register<UnpackSourcesTask>("unpackCommonSources") {
    description = "Unpack kotlin stdlib common sources"
    archiveFiles.from(commonSourcesConfig)
    targetDir.set(unpackedCommonSourcesDir)
}
