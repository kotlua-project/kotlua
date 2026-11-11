package io.github.kotlua.gradle

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.Action

val SourceSet.kotlin: SourceDirectorySet
    get() = (this as ExtensionAware).extensions.getByName("kotlin") as SourceDirectorySet

fun SourceSet.kotlin(configure: Action<SourceDirectorySet>) {
    configure.execute(kotlin)
}
