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
package io.github.kotlua

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

internal fun Project.configureDetekt() {
    apply(plugin = "dev.detekt")
    extensions.configure<DetektExtension> {
        config.setFrom(rootDir.resolve("config/detekt/detekt.yml").path)
        toolVersion.set(libs.findVersion("detekt").get().requiredVersion)
        buildUponDefaultConfig.set(true)
    }
    val projectDir = projectDir
    tasks.withType<Detekt>().configureEach {
        exclude {
            it.file.relativeTo(projectDir).startsWith("build")
        }
    }
    tasks.named("check").configure {
        dependsOn(tasks.withType<Detekt>())
    }
}
