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
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.kotlua.kotlin)
}

kotlin {
    compilerOptions {
        optIn.add("org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI")
    }
}

dependencies {
    implementation(projects.luaAst)
    implementation(projects.luaParser)
    implementation(libs.kotlin.compiler.embeddable)

    testImplementation(libs.luaj.jse)
}

val stdlibKlibFile =
    rootProject.layout.projectDirectory.file("lua-stdlib/minimal/build/lua/classes/kotlin-stdlib-lua.klib")
val localPropertiesFile = rootProject.layout.projectDirectory.file("local.properties")
val luaExecutable =
    providers
        .fileContents(localPropertiesFile)
        .asText
        .map { content ->
            Properties()
                .apply { load(StringReader(content)) }
                .getProperty("kotlua.test.luaExecutable")
                ?.trim()
                .orEmpty()
        }.orNull
        ?.takeIf { it.isNotEmpty() }

tasks.test {
    useJUnitPlatform()

    systemProperty("kotlua.test.stdlib", stdlibKlibFile.asFile.absolutePath)
    if (luaExecutable != null) {
        systemProperty("kotlua.test.luaExecutable", luaExecutable)
    }
}
