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

import io.github.kotlua.config.outputDir
import org.jetbrains.kotlin.cli.pipeline.CheckCompilationErrors
import org.jetbrains.kotlin.cli.pipeline.ConfigurationPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.PipelinePhase
import org.jetbrains.kotlin.cli.pipeline.executePhaseIsolatedWithActions

object LuaBackendPipelinePhase : PipelinePhase<ConfigurationPipelineArtifact, LuaBackendPipelineArtifact>(
    name = "LuaBackendPipelinePhase",
    preActions = emptySet(),
    postActions = setOf(CheckCompilationErrors.CheckDiagnosticCollector),
) {
    override fun executePhase(input: ConfigurationPipelineArtifact): LuaBackendPipelineArtifact? {
        val configuration = input.configuration
        val outputDirPath = configuration.outputDir

        val loadedKlibArtifact = LuaIrLoadingPipelinePhase.executePhaseIsolatedWithActions(input) ?: return null
        val loweredIr = LuaIrLoweringPipelinePhase.executePhaseIsolatedWithActions(loadedKlibArtifact) ?: return null
        val output = LuaCodegenPipelinePhase.executePhaseIsolatedWithActions(loweredIr) ?: return null

        // writeAll

        return output
    }
}
