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
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.github.kotlua.KotluaPublishingExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugins.signing.SigningExtension

abstract class PublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.vanniktech.maven.publish.base")

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            val kotlinVersion = libs.findVersion("kotlin").get().requiredVersion
            val kotluaVersion =
                providers.gradleProperty("kotlua.version").orNull
                    ?: error("kotlua.version is not provided for ${target.name}")

            val resolvedGroup = "io.github.kotlua-project"
            val resolvedVersion = "$kotlinVersion-$kotluaVersion"

            group = resolvedGroup
            version = resolvedVersion

            val ext = extensions.create<KotluaPublishingExtension>("kotluaPublishing")
            ext.artifactName.convention(name)
            ext.artifactDescription.convention("Kotlua module: $name")

            val isSnapshot = resolvedVersion.endsWith("-SNAPSHOT")
            val hasSigningKey =
                providers.gradleProperty("signingInMemoryKey").isPresent ||
                    providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent ||
                    providers.gradleProperty("signing.secretKeyRingFile").isPresent

            extensions.configure<MavenPublishBaseExtension> {
                coordinates(
                    groupId = resolvedGroup,
                    artifactId = name,
                    version = resolvedVersion,
                )

                publishToMavenCentral(automaticRelease = true)

                if (hasSigningKey || !isSnapshot) {
                    signAllPublications()
                }

                pom {
                    name.set(ext.artifactName)
                    description.set(ext.artifactDescription)
                    inceptionYear.set("2026")
                    url.set("https://github.com/kotlua-project/kotlua")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("kotlua")
                            name.set("Kotlua Contributors")
                            url.set("https://github.com/kotlua-project")
                        }
                    }
                    scm {
                        url.set("https://github.com/kotlua-project/kotlua")
                        connection.set("scm:git:git://github.com/kotlua-project/kotlua.git")
                        developerConnection.set("scm:git:ssh://git@github.com/kotlua-project/kotlua.git")
                    }
                }
            }

            if (hasSigningKey || !isSnapshot) {
                extensions.configure<SigningExtension> {
                    isRequired = !isSnapshot
                }
            }
        }
    }
}
