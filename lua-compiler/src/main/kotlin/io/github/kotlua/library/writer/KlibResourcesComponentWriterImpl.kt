/*
 * Copyright 2010-2026 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Copyright 2026 The kotlua Authors.
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
 *
 * Notice: This file has been modified from its original form in the Kotlin project.
 */
package io.github.kotlua.library.writer

import org.jetbrains.kotlin.library.KlibComponentLayout
import org.jetbrains.kotlin.library.KlibConstants.KLIB_DEFAULT_COMPONENT_NAME
import org.jetbrains.kotlin.library.KlibConstants.KLIB_RESOURCES_FOLDER_NAME
import org.jetbrains.kotlin.library.writer.KlibComponentWriter
import java.nio.file.Path
import kotlin.io.path.createDirectories

internal object KlibResourcesComponentWriterImpl : KlibComponentWriter {
    override fun writeTo(root: Path) {
        KlibResourcesComponentLayout(root).resourcesDir.createDirectories()
    }
}

private class KlibResourcesComponentLayout(
    root: Path,
) : KlibComponentLayout(root) {
    val resourcesDir: Path
        get() = root.resolve(KLIB_DEFAULT_COMPONENT_NAME).resolve(KLIB_RESOURCES_FOLDER_NAME)
}
