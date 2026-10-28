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
import javax.inject.Inject
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    base
    alias(libs.plugins.kotlua.spotless)
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

dependencies {
    compilerClasspath(project(":lua-compiler"))
    commonSourcesConfig(libs.kotlin.stdlib)
}

val unpackedCommonSourcesDir = layout.buildDirectory.dir("unpacked-common-sources")
val commonSourcesDir = unpackedCommonSourcesDir.map { it.dir("commonMain") }
val commonNonJvmMainDir = unpackedCommonSourcesDir.map { it.dir("commonNonJvmMain") }

abstract class UnpackSourcesTask @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val fileSystemOperations: FileSystemOperations
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

val libsDir = layout.buildDirectory.dir("libs")
val luaKlibFile = libsDir.map { it.file("kotlin-stdlib-lua.klib") }

val compileLuaStdlib = tasks.register<JavaExec>("compileLuaStdLib") {
    group = "build"
    description = "Compiles kotlin-stdlib-lua.klib"
    dependsOn(unpackCommonSources)

    mainClass.set("io.github.kotlua.cli.compiler.K2LuaCompiler")
    classpath = compilerClasspath

    inputs.dir(commonSourcesDir)
    inputs.dir(commonNonJvmMainDir)
    inputs.dir(file("src/lua"))
    outputs.file(luaKlibFile)

    args(
        listOf(
            "-Xallow-kotlin-package",
            "-Xstdlib-compilation",
            "-Xreturn-value-checker=full",
            "-Xexpect-actual-classes",
            "-Xmulti-platform",
            "-Xname-based-destructuring=complete",
            "-opt-in=kotlin.ExperimentalMultiplatform",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlin.ExperimentalStdlibApi",
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
            "-opt-in=kotlin.uuid.ExperimentalUuidApi",
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlin.io.encoding.ExperimentalEncodingApi",
            "-ir-output-dir", libsDir.get().asFile.absolutePath,
            "-ir-output-name", "kotlin-stdlib-lua",

            "-Xcommon-sources=${commonSourcesDir.get().asFile.absolutePath}",

            file("src/lua").absolutePath,
            commonSourcesDir.get().asFile.absolutePath,
            commonNonJvmMainDir.get().asFile.absolutePath,
        )
    )
}

tasks.named("assemble") {
    dependsOn(compileLuaStdlib)
}
