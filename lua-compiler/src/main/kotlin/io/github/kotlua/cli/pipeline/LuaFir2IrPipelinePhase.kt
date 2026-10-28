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

import io.github.kotlua.ir.LuaDeserializedDescriptorFactory
import io.github.kotlua.ir.LuaFactories
import io.github.kotlua.ir.lower.serialization.ir.LuaManglerIr
import io.github.kotlua.library.isLuaStdlib
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.common.diagnosticsCollector
import org.jetbrains.kotlin.cli.hasMessageCollectorErrors
import org.jetbrains.kotlin.cli.pipeline.CheckCompilationErrors
import org.jetbrains.kotlin.cli.pipeline.PerformanceNotifications
import org.jetbrains.kotlin.cli.pipeline.PipelinePhase
import org.jetbrains.kotlin.compiler.plugin.getCompilerExtensions
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector
import org.jetbrains.kotlin.fir.backend.Fir2IrConfiguration
import org.jetbrains.kotlin.fir.backend.Fir2IrExtensions
import org.jetbrains.kotlin.fir.backend.Fir2IrVisibilityConverter
import org.jetbrains.kotlin.fir.descriptors.FirModuleDescriptor
import org.jetbrains.kotlin.fir.pipeline.AllModulesFrontendOutput
import org.jetbrains.kotlin.fir.pipeline.Fir2IrActualizedResult
import org.jetbrains.kotlin.fir.pipeline.SingleModuleFrontendOutput
import org.jetbrains.kotlin.fir.pipeline.convertToIrAndActualize
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.storage.LockBasedStorageManager

object LuaFir2IrPipelinePhase : PipelinePhase<LuaFrontendPipelineArtifact, LuaFir2IrPipelineArtifact>(
    name = "LuaFir2IrPipelinePhase",
    preActions = setOf(PerformanceNotifications.TranslationToIrStarted),
    postActions = setOf(PerformanceNotifications.TranslationToIrFinished, CheckCompilationErrors.CheckDiagnosticCollector),
) {
    override fun executePhase(input: LuaFrontendPipelineArtifact): LuaFir2IrPipelineArtifact {
        val firResult = input.frontendOutput
        val resolvedLibraries = input.resolvedLibraries
        val configuration = input.configuration
        val diagnosticsReporter = configuration.diagnosticsCollector

        val fir2IrActualizedResult =
            transformFirToIr(
                configuration = configuration,
                resolvedLibraries = resolvedLibraries,
                firOutputs = firResult.outputs,
                diagnosticsReporter = diagnosticsReporter,
            )

        return LuaFir2IrPipelineArtifact(
            result = fir2IrActualizedResult,
            frontendOutput = firResult,
            configuration = configuration,
            hasErrors = input.hasErrors || configuration.hasMessageCollectorErrors() || diagnosticsReporter.hasErrors,
        )
    }

    private fun transformFirToIr(
        configuration: CompilerConfiguration,
        resolvedLibraries: List<KotlinLibrary>,
        firOutputs: List<SingleModuleFrontendOutput>,
        diagnosticsReporter: BaseDiagnosticsCollector,
    ): Fir2IrActualizedResult {
        val fir2IrExtensions = Fir2IrExtensions.Default

        var builtInsModule: KotlinBuiltIns? = null
        val dependencies = mutableListOf<ModuleDescriptorImpl>()

        val librariesDescriptors =
            resolvedLibraries.map { resolvedLibrary ->
                val storageManager = LockBasedStorageManager("ModulesStructure")

                val moduleDescriptor =
                    LuaFactories.LuaDeserializedDescriptorFactory.createDescriptorOptionalBuiltIns(
                        resolvedLibrary,
                        configuration.languageVersionSettings,
                        storageManager,
                        builtInsModule,
                        lookupTracker = LookupTracker.DO_NOTHING,
                    )
                dependencies += moduleDescriptor
                moduleDescriptor.setDependencies(ArrayList(dependencies))

                val isBuiltIns = resolvedLibrary.isLuaStdlib
                if (isBuiltIns) builtInsModule = moduleDescriptor.builtIns

                moduleDescriptor
            }

        val firResult = AllModulesFrontendOutput(firOutputs)

        return firResult.convertToIrAndActualize(
            fir2IrExtensions = fir2IrExtensions,
            fir2IrConfiguration =
                Fir2IrConfiguration.forKlibCompilation(
                    configuration,
                    diagnosticsReporter,
                ),
            irGeneratorExtensions = configuration.getCompilerExtensions(IrGenerationExtension),
            irMangler = LuaManglerIr,
            visibilityConverter = Fir2IrVisibilityConverter.Default,
            kotlinBuiltIns = builtInsModule ?: DefaultBuiltIns.Instance,
            typeSystemContextProvider = ::IrTypeSystemContextImpl,
            specialAnnotationsProvider = null,
            extraActualDeclarationExtractorsInitializer = { emptyList() },
        ) { irModuleFragment ->
            (irModuleFragment.descriptor as? FirModuleDescriptor)?.let {
                it.allDependencyModules = librariesDescriptors
            }
        }
    }
}
