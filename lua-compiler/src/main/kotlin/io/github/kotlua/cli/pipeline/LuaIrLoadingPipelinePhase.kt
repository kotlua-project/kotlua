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
package io.github.kotlua.cli.pipeline

import io.github.kotlua.config.includes
import io.github.kotlua.config.libraries
import io.github.kotlua.ir.loadIr
import io.github.kotlua.ir.loadLuaKlibs
import io.github.kotlua.ir.lower.serialization.ir.LuaModulesStructure
import io.github.kotlua.library.LuaKlibPlatformChecker
import org.jetbrains.kotlin.cli.pipeline.ConfigurationPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.PerformanceNotifications
import org.jetbrains.kotlin.cli.pipeline.PipelinePhase
import org.jetbrains.kotlin.ir.declarations.impl.IrFactoryImpl
import java.io.File

object LuaIrLoadingPipelinePhase : PipelinePhase<ConfigurationPipelineArtifact, LuaLoadedIrPipelineArtifact>(
    name = "LuaIrLoadingPipelinePhase",
    preActions = setOf(PerformanceNotifications.TranslationToIrFinished),
    postActions = setOf(PerformanceNotifications.TranslationToIrFinished),
) {
    override fun executePhase(input: ConfigurationPipelineArtifact): LuaLoadedIrPipelineArtifact {
        val configuration = input.configuration
        val includes = configuration.includes!!
        val includesPath = File(includes).canonicalPath
        val mainLibPath = configuration.libraries.find { File(it).canonicalPath == includesPath }
            ?: error("No library with name $includes ($includesPath) found")

        val klibs = loadLuaKlibs(configuration, LuaKlibPlatformChecker)
        val module = LuaModulesStructure(
            mainModulePath = mainLibPath,
            compilerConfiguration = configuration,
            klibs = klibs
        )
        val irFactory = IrFactoryImpl
        val loadedIr = loadIr(module, irFactory)
        return LuaLoadedIrPipelineArtifact(
            moduleInfo = loadedIr,
            configuration = configuration
        )
    }
}
