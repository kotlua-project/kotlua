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

import io.github.kotlua.ir.LuaIrBackendContext
import org.jetbrains.kotlin.backend.common.IrModuleInfo
import org.jetbrains.kotlin.cli.pipeline.*
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.pipeline.AllModulesFrontendOutput
import org.jetbrains.kotlin.fir.pipeline.Fir2IrActualizedResult
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.library.KotlinLibrary
import java.io.File

data class LuaFrontendPipelineArtifact(
    override val frontendOutput: AllModulesFrontendOutput,
    override val configuration: CompilerConfiguration,
    val resolvedLibraries: List<KotlinLibrary>,
    val hasErrors: Boolean,
) : FrontendPipelineArtifact() {
    @CliPipelineInternals(OPT_IN_MESSAGE)
    override fun withCompilerConfiguration(newConfiguration: CompilerConfiguration): LuaFrontendPipelineArtifact =
        copy(configuration = newConfiguration)

    override fun withNewFrontendOutputImpl(newFrontendOutput: AllModulesFrontendOutput): FrontendPipelineArtifact =
        copy(frontendOutput = newFrontendOutput)
}

data class LuaSerializedKlibPipelineArtifact(
    val outputKlibPath: String,
    override val configuration: CompilerConfiguration,
) : PipelineArtifact() {
    @CliPipelineInternals(OPT_IN_MESSAGE)
    override fun withCompilerConfiguration(newConfiguration: CompilerConfiguration): LuaSerializedKlibPipelineArtifact =
        copy(configuration = newConfiguration)
}

data class LuaFir2IrPipelineArtifact(
    override val result: Fir2IrActualizedResult,
    val frontendOutput: AllModulesFrontendOutput,
    override val configuration: CompilerConfiguration,
    val hasErrors: Boolean,
) : Fir2IrPipelineArtifact() {
    @CliPipelineInternals(OPT_IN_MESSAGE)
    override fun withCompilerConfiguration(newConfiguration: CompilerConfiguration): LuaFir2IrPipelineArtifact =
        copy(configuration = newConfiguration)
}

data class LuaLoadedIrPipelineArtifact(
    override val moduleInfo: IrModuleInfo,
    override val configuration: CompilerConfiguration,
) : LoadedIrPipelineArtifact() {
    @CliPipelineInternals(OPT_IN_MESSAGE)
    override fun withCompilerConfiguration(newConfiguration: CompilerConfiguration): LuaLoadedIrPipelineArtifact =
        copy(configuration = newConfiguration)
}

data class LuaLoweredIrPipelineArtifact(
    val context: LuaIrBackendContext,
    val mainModule: IrModuleFragment,
    val allModules: List<IrModuleFragment>,
    override val configuration: CompilerConfiguration,
) : PipelineArtifact() {
    @CliPipelineInternals(OPT_IN_MESSAGE)
    override fun withCompilerConfiguration(newConfiguration: CompilerConfiguration): LuaLoweredIrPipelineArtifact =
        copy(configuration = newConfiguration)
}

data class LuaCompiledResult(
    val mainFileName: String,
    val luaSourceCode: String,
    val sourceMap: String? = null,
    // require() modules
    val additionalFiles: Map<String, String> = emptyMap(),
)

data class LuaBackendPipelineArtifact(
    val result: LuaCompiledResult,
    val outputDir: File,
    override val configuration: CompilerConfiguration,
) : PipelineArtifact() {
    @CliPipelineInternals(OPT_IN_MESSAGE)
    override fun withCompilerConfiguration(newConfiguration: CompilerConfiguration): LuaBackendPipelineArtifact =
        copy(configuration = newConfiguration)
}
