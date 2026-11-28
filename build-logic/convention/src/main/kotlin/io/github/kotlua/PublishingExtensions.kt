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

import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

interface KotluaPublishingExtension {
    val artifactName: Property<String>
    val artifactDescription: Property<String>
}

fun Project.configureKotluaPom(
    name: String,
    description: String,
) {
    extensions.configure<KotluaPublishingExtension> {
        artifactName.set(name)
        artifactDescription.set(description)
    }
}

fun Project.publishKotluaJvmLibrary(
    artifactId: String = name,
    pomName: String = artifactId,
    description: String = "Kotlua module: $artifactId",
) {
    configureKotluaPom(pomName, description)
    extensions.configure<MavenPublishBaseExtension> {
        coordinates(artifactId = artifactId)
        configure(
            JavaLibrary(
                javadocJar = JavadocJar.Javadoc(),
                sourcesJar = SourcesJar.Sources(),
            ),
        )
    }
}

fun Project.publishKotluaGradlePlugin(
    artifactId: String = "kotlua-gradle-plugin",
    pomName: String = "Kotlua Gradle Plugin",
    description: String = "Gradle plugin for compiling Kotlin to Lua",
) {
    configureKotluaPom(pomName, description)
    extensions.configure<MavenPublishBaseExtension> {
        coordinates(artifactId = artifactId)
        configure(
            GradlePlugin(
                javadocJar = JavadocJar.Javadoc(),
                sourcesJar = SourcesJar.Sources(),
            ),
        )
    }
}

fun Project.publishKotluaKlib(
    artifactId: String,
    klibArtifact: Any,
    sourcesFiles: Any? = null,
    pomName: String = artifactId,
    description: String = "Kotlua KLIB: $artifactId",
) {
    configureKotluaPom(pomName, description)
    extensions.configure<MavenPublishBaseExtension> {
        coordinates(artifactId = artifactId)
    }

    val emptyJavadocJar =
        tasks.register<Jar>("emptyJavadocJar") {
            archiveClassifier.set("javadoc")
        }
    val klibSourcesJar =
        tasks.register<Jar>("klibSourcesJar") {
            archiveClassifier.set("sources")
            if (sourcesFiles != null) {
                from(sourcesFiles)
            }
        }

    extensions.configure<PublishingExtension> {
        publications.create<MavenPublication>("klib") {
            this.artifactId = artifactId
            artifact(klibArtifact) {
                extension = "klib"
            }
            artifact(klibSourcesJar)
            artifact(emptyJavadocJar)
            pom.packaging = "klib"
        }
    }
}
