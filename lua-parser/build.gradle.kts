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
import com.strumenta.antlrkotlin.gradle.AntlrKotlinTask
import io.github.kotlua.publishKotluaJvmLibrary

plugins {
    alias(libs.plugins.kotlua.kotlin)
    alias(libs.plugins.antlr4.kotlin)
    alias(libs.plugins.kotlua.publishing)
}

dependencies {
    api(projects.luaAst)
}

publishKotluaJvmLibrary(
    artifactId = "kotlua-parser",
    description = "Lua 5.1 parser",
)

val antlrGeneratedPackage = "io.github.kotlua.parser.generated"
val antlrGeneratedDir = "generatedAntlr/${antlrGeneratedPackage.replace(".", "/")}"

val generateKotlinGrammarSource = tasks.register<AntlrKotlinTask>("generateKotlinGrammarSource") {
    description = "Generate kotlin grammar source"
    dependsOn("cleanGenerateKotlinGrammarSource")

    source = fileTree(layout.projectDirectory.dir("antlr")) {
        include("**/*.g4")
    }

    packageName = antlrGeneratedPackage
    arguments = listOf("-visitor")
    outputDirectory = layout.buildDirectory.dir(antlrGeneratedDir).get().asFile
}

kotlin {
    sourceSets {
        main {
            kotlin {
                srcDir(layout.buildDirectory.dir(antlrGeneratedDir))
            }
            dependencies {
                implementation(libs.antlr.kotlin.runtime)
            }
        }
    }
}
