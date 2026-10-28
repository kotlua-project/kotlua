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

import io.github.kotlua.cli.compiler.K2LuaCompilerArguments
import org.jetbrains.kotlin.backend.common.phaser.then
import org.jetbrains.kotlin.cli.pipeline.AbstractCliPipeline
import org.jetbrains.kotlin.cli.pipeline.ArgumentsPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.FrontendFilesForPluginsGenerationPipelinePhase
import org.jetbrains.kotlin.cli.pipeline.PipelineContext
import org.jetbrains.kotlin.config.phaser.CompilerPhase
import org.jetbrains.kotlin.util.PerformanceManager

class LuaCliPipeline(
    override val defaultPerformanceManager: PerformanceManager,
) : AbstractCliPipeline<K2LuaCompilerArguments>() {
    override fun createCompoundPhase(
        arguments: K2LuaCompilerArguments,
    ): CompilerPhase<PipelineContext, ArgumentsPipelineArtifact<K2LuaCompilerArguments>, *> =
        when {
            arguments.includes != null -> createCodeGenerationPhase()
            else -> createKlibSerializationPhase()
        }

    private fun createKlibSerializationPhase(): CompilerPhase<PipelineContext, ArgumentsPipelineArtifact<K2LuaCompilerArguments>, *> =
        LuaConfigurationPhase then
            LuaFrontendPipelinePhase then
            FrontendFilesForPluginsGenerationPipelinePhase() then
            LuaFir2IrPipelinePhase then
            // LuaKlibInliningPipelinePhase then
            LuaKlibSerializationPipelinePhase

    private fun createCodeGenerationPhase(): CompilerPhase<PipelineContext, ArgumentsPipelineArtifact<K2LuaCompilerArguments>, *> =
        LuaConfigurationPhase then
            LuaBackendPipelinePhase
}
