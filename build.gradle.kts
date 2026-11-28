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
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt) apply true
    alias(libs.plugins.kotlua.root)
}

val publishAllToMavenLocal = tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Publishes all Kotlua artifacts and Gradle plugin to mavenLocal"
    dependsOn(gradle.includedBuild("gradle-plugin").task(":publishToMavenLocal"))
}

val publishAllToMavenCentral = tasks.register("publishAllToMavenCentral") {
    group = "publishing"
    description = "Publishes all Kotlua artifacts and Gradle plugin to Maven Central"
    dependsOn(gradle.includedBuild("gradle-plugin").task(":publishToMavenCentral"))
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish.base") {
        publishAllToMavenLocal.configure {
            dependsOn(tasks.named("publishToMavenLocal"))
        }
        publishAllToMavenCentral.configure {
            dependsOn(tasks.named("publishToMavenCentral"))
        }
    }
}
