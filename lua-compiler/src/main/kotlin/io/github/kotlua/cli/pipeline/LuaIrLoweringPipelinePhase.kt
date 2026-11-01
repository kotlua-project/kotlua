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
import io.github.kotlua.ir.lower.serialization.ir.LuaIrLinker
import io.github.kotlua.ir.luaLowerings
import org.jetbrains.kotlin.backend.common.CompilationException
import org.jetbrains.kotlin.backend.common.linkage.issues.checkNoUnboundSymbols
import org.jetbrains.kotlin.cli.common.reportCompilationException
import org.jetbrains.kotlin.cli.pipeline.PerformanceNotifications
import org.jetbrains.kotlin.cli.pipeline.PipelinePhase
import org.jetbrains.kotlin.config.phaser.PhaserState
import org.jetbrains.kotlin.ir.util.ExternalDependenciesGenerator

object LuaIrLoweringPipelinePhase : PipelinePhase<LuaLoadedIrPipelineArtifact, LuaLoweredIrPipelineArtifact>(
    name = "LuaIrLoweringPipelinePhase",
    preActions = setOf(PerformanceNotifications.IrLoweringStarted),
    postActions = setOf(PerformanceNotifications.IrLoweringFinished),
) {
    override fun executePhase(input: LuaLoadedIrPipelineArtifact): LuaLoweredIrPipelineArtifact? =
        try {
            lowerIr(input)
        } catch (e: CompilationException) {
            input.configuration.reportCompilationException(e)
            null
        }

    private fun lowerIr(input: LuaLoadedIrPipelineArtifact): LuaLoweredIrPipelineArtifact {
        val configuration = input.configuration
        val module = input.moduleInfo
        val moduleFragment = module.module
        val dependencies = module.dependencies
        val irBuiltIns = module.bultins
        val symbolTable = module.symbolTable
        val deserializer = module.deserializer
        require(deserializer is LuaIrLinker) {
            "Lua compiler needs LuaIrLinker, but got ${deserializer.javaClass.name}"
        }
        val moduleDescriptor = moduleFragment.descriptor
        val context = LuaIrBackendContext(
            module = moduleDescriptor,
            irBuiltIns = irBuiltIns,
            symbolTable = symbolTable,
            configuration = configuration
        )
        val irProviders = listOf(deserializer)
        ExternalDependenciesGenerator(symbolTable, irProviders).generateUnboundSymbolsAsDependencies()
        deserializer.postProcess(irBuiltIns, inOrAfterLinkageStep = true)
        deserializer.checkNoUnboundSymbols(symbolTable = symbolTable, whenDetected = "at the end of IR linkage process")
        deserializer.clear()
        val sortedModuleDependencies = deserializer.moduleDependencyTracker.reverseTopoOrder(moduleDependencies = dependencies)
        val allModules = sortedModuleDependencies.all
        val phaserState = PhaserState()
        luaLowerings.forEachIndexed { _, lowering ->
            allModules.forEach { module ->
                lowering.invoke(context.phaseConfig, phaserState, context, module)
            }
        }

        return LuaLoweredIrPipelineArtifact(
            context = context,
            mainModule = moduleFragment,
            allModules = allModules,
            configuration = configuration
        )
    }
}
