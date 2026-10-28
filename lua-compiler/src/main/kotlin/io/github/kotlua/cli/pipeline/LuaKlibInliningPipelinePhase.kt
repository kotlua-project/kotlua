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

import io.github.kotlua.ir.LuaPreSerializationLoweringContext
import io.github.kotlua.ir.luaLoweringsOfTheFirstPhase
import org.jetbrains.kotlin.backend.common.phaser.PhaseEngine
import org.jetbrains.kotlin.cli.common.diagnosticsCollector
import org.jetbrains.kotlin.cli.common.runPreSerializationLoweringPhases
import org.jetbrains.kotlin.cli.pipeline.CheckCompilationErrors
import org.jetbrains.kotlin.cli.pipeline.PerformanceNotifications
import org.jetbrains.kotlin.cli.pipeline.PipelinePhase
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.config.phaseConfig
import org.jetbrains.kotlin.config.phaser.PhaserState
import org.jetbrains.kotlin.ir.KtDiagnosticReporterWithImplicitIrBasedContext

object LuaKlibInliningPipelinePhase : PipelinePhase<LuaFir2IrPipelineArtifact, LuaFir2IrPipelineArtifact>(
    name = "LuaKlibInliningPipelinePhase",
    preActions = setOf(PerformanceNotifications.IrPreLoweringStarted),
    postActions =
        setOf(
            PerformanceNotifications.IrPreLoweringFinished,
            CheckCompilationErrors.CheckDiagnosticCollector,
        ),
) {
    override fun executePhase(input: LuaFir2IrPipelineArtifact): LuaFir2IrPipelineArtifact {
        val fir2IrResult = input.result
        val configuration = input.configuration

        val irDiagnosticReporter =
            KtDiagnosticReporterWithImplicitIrBasedContext(
                diagnosticReporter = configuration.diagnosticsCollector,
                languageVersionSettings = configuration.languageVersionSettings,
            )

        val transformedResult =
            PhaseEngine(
                phaseConfig = configuration.phaseConfig!!,
                phaserState = PhaserState(),
                context = LuaPreSerializationLoweringContext(fir2IrResult.irBuiltIns, configuration, irDiagnosticReporter),
            ).runPreSerializationLoweringPhases(
                fir2IrActualizedResult = fir2IrResult,
                lowerings = luaLoweringsOfTheFirstPhase(configuration.languageVersionSettings),
            )

        return input.copy(result = transformedResult)
    }
}
