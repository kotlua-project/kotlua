import io.github.kotlua.gradle.Kotlin2LuaCompile

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

plugins {
    base
    alias(libs.plugins.kotlua.spotless)
    alias(libs.plugins.kotlua.gradlePlugin)
}

dependencies {
    kotluaCompilerClasspath(projects.luaCompiler)
}

kotlua {
    multiPlatformEnabled = true
    compilerOptions {
        freeCompilerArgs.addAll(
            listOf(
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

val parentDir: File = project.rootDir.parentFile
val builtins = parentDir.resolve("builtins")

tasks {
    withType<Kotlin2LuaCompile>().configureEach {

        source("src/", "runtime/", "builtins/")
    }
}
