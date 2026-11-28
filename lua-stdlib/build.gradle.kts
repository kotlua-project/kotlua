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
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import io.github.kotlua.publishKotluaKlib

plugins {
    base
    alias(libs.plugins.kotlua.gradlePlugin)
    alias(libs.plugins.kotlua.publishing)
}

val commonSourcesConfig = configurations.create("commonSourcesConfig") {
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType::class.java, DocsType.SOURCES))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.common)
    }
}

val commonMainFullSourcesElements = configurations.consumable("commonMainFullSourcesElements")
val commonNonJvmMainFullSourcesElements = configurations.consumable("commonNonJvmMainFullSourcesElements")

dependencies {
    kotluaCompilerClasspath(projects.luaCompiler)
    commonSourcesConfig(libs.kotlin.stdlib)
}

kotlua {
    multiPlatformEnabled = true
    compilerOptions {
        nopack = false
        moduleName = "kotlin-stdlib-lua"
        freeCompilerArgs.addAll(
            listOf(
                "-Xallow-kotlin-package",
                "-Xstdlib-compilation",
                "-Xreturn-value-checker=full",
                "-Xexpect-actual-classes",
                "-Xcontext-parameters",
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

val unpackedCommonSourcesDir = layout.buildDirectory.dir("unpacked-common-sources")

val unpackCommonSources = tasks.register<UnpackSourcesTask>("unpackCommonSources") {
    description = "Unpack kotlin stdlib common sources"
    archiveFiles.from(commonSourcesConfig)
    targetDir.set(unpackedCommonSourcesDir)
}

val commonMainFullSources = tasks.register<Sync>("commonMainFullSources") {
    from(unpackCommonSources.flatMap { it.targetDir.dir("commonMain") })
    from(unpackCommonSources.flatMap { it.targetDir.dir("commonNonJvmMain") }) {
        include("kotlin/reflect/findAssociatedObject.kt")
        include("kotlin/reflect/AssociatedObjects.kt")
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("commonMainFullSources"))
}

val commonNonJvmMainFullSources = tasks.register<Sync>("commonNonJvmMainFullSources") {
    from(unpackCommonSources.flatMap { it.targetDir.dir("commonNonJvmMain") }) {
        exclude("kotlin/reflect/findAssociatedObject.kt")
        exclude("kotlin/reflect/AssociatedObjects.kt")
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("commonNonJvmMainFullSources"))
}

val luaMainFullSourcesElements = configurations.consumable("luaMainFullSourcesElements")

val luaMainFullSources = tasks.register<Sync>("luaMainFullSources") {
    from("src") { into("src") }
    from("runtime") { into("runtime") }
    from("builtins") { into("builtins") }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("luaMainFullSources"))
}

artifacts {
    add(commonMainFullSourcesElements.name, commonMainFullSources)
    add(commonNonJvmMainFullSourcesElements.name, commonNonJvmMainFullSources)
    add(luaMainFullSourcesElements.name, luaMainFullSources)
}

publishKotluaKlib(
    artifactId = "kotlin-stdlib-lua",
    klibArtifact = tasks.named<Kotlin2LuaCompile>("kotlinLuaCompile").flatMap { it.destinationDirectory.file("kotlin-stdlib-lua.klib") },
    sourcesFiles = listOf(commonMainFullSources, commonNonJvmMainFullSources, luaMainFullSources),
    description = "Kotlin stdlib for Lua"
)

sourceSets {
    commonMain {
        kotlin.setSrcDirs(emptyList<Any>())
        kotlin.srcDir(commonMainFullSources)
    }
    luaMain {
        kotlin.setSrcDirs(listOf("src", "builtins", "runtime"))
        kotlin.srcDir(commonNonJvmMainFullSources)
        resources.setSrcDirs(emptyList<Any>())
    }
}
