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
import io.github.kotlua.config.outputName
import io.github.kotlua.ir.serializeModuleIntoKlib
import org.jetbrains.kotlin.cli.common.diagnosticsCollector
import org.jetbrains.kotlin.cli.pipeline.PipelinePhase
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.config.perfManager
import org.jetbrains.kotlin.fir.pipeline.Fir2KlibMetadataSerializer
import org.jetbrains.kotlin.ir.KtDiagnosticReporterWithImplicitIrBasedContext
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.pathString

object LuaKlibSerializationPipelinePhase : PipelinePhase<LuaFir2IrPipelineArtifact, LuaSerializedKlibPipelineArtifact>(
    name = "LuaKlibSerializationPipelinePhase",
) {
    override fun executePhase(input: LuaFir2IrPipelineArtifact): LuaSerializedKlibPipelineArtifact {
        val fir2IrResult = input.result
        val firResult = input.frontendOutput
        val configuration = input.configuration
        val irDiagnosticReporter =
            KtDiagnosticReporterWithImplicitIrBasedContext(
                configuration.diagnosticsCollector,
                configuration.languageVersionSettings,
            )
        val outputKlibPath = configuration.computeOutputKlibPath()

        val fir2KlibMetadataSerializer =
            Fir2KlibMetadataSerializer(
                compilerConfiguration = configuration,
                firOutputs = firResult.outputs,
                fir2IrActualizedResult = fir2IrResult,
                produceHeaderKlib = false,
            )

        serializeModuleIntoKlib(
            moduleName = configuration[CommonConfigurationKeys.MODULE_NAME]!!,
            configuration = configuration,
            diagnosticReporter = irDiagnosticReporter,
            metadataSerializer = fir2KlibMetadataSerializer,
            klibPath = outputKlibPath,
            moduleFragment = fir2IrResult.irModuleFragment,
            irBuiltIns = fir2IrResult.irBuiltIns,
            cleanFiles = emptyList(),
            nopack = false,
            performanceManager = configuration.perfManager,
        )

        return LuaSerializedKlibPipelineArtifact(
            outputKlibPath = outputKlibPath.pathString,
            configuration = configuration,
        )
    }
}

internal fun CompilerConfiguration.computeOutputKlibPath(): Path {
    val basePath = outputDir!!.resolve("${outputName!!}.klib").normalize()
    return basePath.toPath().absolute()
}
