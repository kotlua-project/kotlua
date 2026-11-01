package io.github.kotlua.gradle

import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import java.io.File
import javax.inject.Inject

internal interface ProducesKlib : Task {
    @get:Inject
    val projectLayout: ProjectLayout

    @get:Internal
    val klibOutput: Provider<File>

    @get:Internal
    val klibDirectory: Provider<Directory>
        get() = projectLayout.dir(klibOutput)
}