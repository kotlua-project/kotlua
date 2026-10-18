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

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

internal fun Project.configureSpotlessForJvm() {
    configureSpotlessCommon()
}

internal fun Project.configureSpotlessForRootProject() {
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<SpotlessExtension> {
        val licenseHeader = rootProject.file("spotless/copyright.txt")
        val firstNonCommentLine = "(^(?![\\/ ]\\*).*$)"

        kotlin {
            target("build-logic/convention/src/**/*.kt")
            ktlint()
            licenseHeaderFile(licenseHeader, firstNonCommentLine)
            endWithNewline()
        }

        format("kts") {
            target("*.kts", "build-logic/*.kts", "build-logic/convention/*.kts")
            licenseHeaderFile(licenseHeader, firstNonCommentLine)
            endWithNewline()
        }
    }
}

private fun Project.configureSpotlessCommon() {
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<SpotlessExtension> {
        val licenseHeader = rootProject.file("spotless/copyright.txt")
        val firstNonCommentLine = "(^(?![\\/ ]\\*).*$)"

        kotlin {
            target("src/**/*.kt")
            ktlint()
            licenseHeaderFile(licenseHeader, firstNonCommentLine)
            endWithNewline()
        }

        format("kts") {
            target("*.kts")
            licenseHeaderFile(licenseHeader, firstNonCommentLine)
            endWithNewline()
        }
    }
}
