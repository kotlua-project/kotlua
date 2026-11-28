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
import io.github.kotlua.publishKotluaGradlePlugin
import io.github.kotlua.util.GenerateKotluaBuildConfigTask

plugins {
    `kotlin-dsl`
    alias(libs.plugins.kotlua.publishing)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlin.gradle.plugin)
    implementation("$group:kotlua-compiler-api:$version")
    implementation("$group:kotlua-tooling-model:$version")
    compileOnly(libs.kotlin.compiler.embeddable)
}

gradlePlugin {
    plugins {
        register("kotluaGradlePlugin") {
            id =
                libs.plugins.kotlua.gradlePlugin
                    .get()
                    .pluginId
            implementationClass = "io.github.kotlua.gradle.KotlinLuaPlugin"
        }
    }
}

publishKotluaGradlePlugin()

val generateKotluaBuildConfig =
    tasks.register<GenerateKotluaBuildConfigTask>("generateKotluaBuildConfig") {
        kotluaGroup.set(provider { project.group.toString() })
        kotluaVersion.set(provider { project.version.toString() })
        kotlinVersion.set(libs.versions.kotlin)
        outputDir.set(layout.buildDirectory.dir("generated/sources/buildConfig/kotlin"))
    }

sourceSets {
    main {
        kotlin.srcDir(generateKotluaBuildConfig.flatMap { it.outputDir })
    }
}
