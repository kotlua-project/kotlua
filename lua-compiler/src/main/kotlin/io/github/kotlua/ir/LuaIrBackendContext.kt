/*
 * Copyright 2026 The kotlua Authors
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
 */
package io.github.kotlua.ir

import io.github.kotlua.ir.lower.LuaInnerClassesSupport
import io.github.kotlua.ir.utils.LuaInlineClassesUtils
import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.ir.KlibSharedVariablesManager
import org.jetbrains.kotlin.backend.common.ir.SharedVariablesManager
import org.jetbrains.kotlin.backend.common.linkage.partial.createPartialLinkageSupportForLowerings
import org.jetbrains.kotlin.backend.common.linkage.partial.partialLinkageConfig
import org.jetbrains.kotlin.backend.common.lower.InnerClassesSupport
import org.jetbrains.kotlin.cli.common.diagnosticsCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.config.phaseConfig
import org.jetbrains.kotlin.config.phaser.PhaseConfig
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.KtDiagnosticReporterWithImplicitIrBasedContext
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.types.IrTypeSystemContext
import org.jetbrains.kotlin.ir.types.IrTypeSystemContextImpl
import org.jetbrains.kotlin.ir.util.SymbolTable

class LuaIrBackendContext(
    val module: ModuleDescriptor,
    override val irBuiltIns: IrBuiltIns,
    val symbolTable: SymbolTable,
    override val configuration: CompilerConfiguration,
) : CommonBackendContext {
    val phaseConfig = configuration.phaseConfig ?: PhaseConfig()
    override val typeSystem: IrTypeSystemContext = IrTypeSystemContextImpl(irBuiltIns)
    override val inlineClassesUtils = LuaInlineClassesUtils(this)
    override val innerClassesSupport = LuaInnerClassesSupport()
    override val irFactory: IrFactory = symbolTable.irFactory
    override var inVerbosePhase: Boolean = false
    override val diagnosticReporter =
        KtDiagnosticReporterWithImplicitIrBasedContext(
            configuration.diagnosticsCollector,
            configuration.languageVersionSettings,
        )
    override val symbols = BackendLuaSymbols(irBuiltIns, irFactory.stageController)
    override val sharedVariablesManager = KlibSharedVariablesManager(symbols)

    override val partialLinkageSupport =
        createPartialLinkageSupportForLowerings(
            configuration.partialLinkageConfig,
            diagnosticReporter = diagnosticReporter,
        )
}
