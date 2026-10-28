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
package io.github.kotlua.ir

import io.github.kotlua.config.includes
import io.github.kotlua.config.libraries
import org.jetbrains.kotlin.backend.common.LoadedKlibs
import org.jetbrains.kotlin.backend.common.loadFriendLibraries
import org.jetbrains.kotlin.backend.common.reportLoadingProblemsIfAny
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.zipFileSystemAccessor
import org.jetbrains.kotlin.library.KotlinAbiVersion
import org.jetbrains.kotlin.library.loader.KlibLoader
import org.jetbrains.kotlin.library.loader.KlibPlatformChecker

fun loadLuaKlibs(
    configuration: CompilerConfiguration,
    platformChecker: KlibPlatformChecker,
): LoadedKlibs {
    val result =
        KlibLoader {
            libraryPaths(configuration.libraries)
            platformChecker(platformChecker)
            maxPermittedAbiVersion(KotlinAbiVersion.CURRENT)
            configuration.zipFileSystemAccessor?.let { zipFileSystemAccessor(it) }
        }.load()
    result.reportLoadingProblemsIfAny(configuration, allAsErrors = true)
    return LoadedKlibs(
        all = result.librariesStdlibFirst,
        included = result.loadFriendLibraries(listOfNotNull(configuration.includes)).firstOrNull(),
    )
}
