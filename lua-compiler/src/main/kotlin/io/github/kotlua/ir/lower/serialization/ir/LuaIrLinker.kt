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
package io.github.kotlua.ir.lower.serialization.ir

import io.github.kotlua.library.isLuaStdlib
import org.jetbrains.kotlin.backend.common.linkage.partial.PartialLinkageSupportForLinker
import org.jetbrains.kotlin.backend.common.linkage.partial.createPartialLinkageSupportForLinker
import org.jetbrains.kotlin.backend.common.overrides.IrLinkerFakeOverrideProvider
import org.jetbrains.kotlin.backend.common.serialization.BasicIrModuleDeserializer
import org.jetbrains.kotlin.backend.common.serialization.DeserializationStrategy
import org.jetbrains.kotlin.backend.common.serialization.IrModuleDependencyTracker
import org.jetbrains.kotlin.backend.common.serialization.IrModuleDependencyTrackerImpl
import org.jetbrains.kotlin.backend.common.serialization.IrModuleDeserializer
import org.jetbrains.kotlin.backend.common.serialization.KotlinIrLinker
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.PartialLinkageConfig
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.IrDiagnosticReporter
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.KotlinMangler
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.library.KotlinAbiVersion
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.metadata.DeserializedKlibModuleOrigin
import org.jetbrains.kotlin.library.metadata.klibModuleOrigin
import org.jetbrains.kotlin.utils.addToStdlib.runIf
import org.jetbrains.kotlin.utils.memoryOptimizedMap
import kotlin.collections.set

class LuaIrLinker(
    configuration: CompilerConfiguration,
    symbolTable: SymbolTable,
    partialLinkageConfig: PartialLinkageConfig,
    irDiagnosticReporter: IrDiagnosticReporter,
    friendModules: Map<String, Collection<String>> = emptyMap(),
) : KotlinIrLinker(
        currentModule = null,
        configuration = configuration,
        symbolTable = symbolTable,
        exportedDependencies = emptyList(),
        deserializedSymbolPostProcessor = { symbol, signature, fileSymbol ->
            runIf(signature.isLocal) {
                symbol.privateSignature = IdSignature.CompositeSignature(IdSignature.FileSignature(fileSymbol), signature)
            }
            symbol
        },
    ) {
    override val partialLinkageSupport: PartialLinkageSupportForLinker =
        createPartialLinkageSupportForLinker(
            partialLinkageConfig = partialLinkageConfig,
            irFactory = symbolTable.irFactory,
            anyClass = anyClass,
            nothingClass = nothingClass,
            diagnosticReporter = irDiagnosticReporter,
        )

    override val moduleDependencyTracker: IrModuleDependencyTracker = IrModuleDependencyTrackerImpl()

    override val irMangler: KotlinMangler.IrMangler = LuaManglerIr

    override val fakeOverrideBuilder =
        IrLinkerFakeOverrideProvider(
            linker = this,
            symbolTable = symbolTable,
            mangler = irMangler,
            friendModules = friendModules,
            partialLinkageSupport = partialLinkageSupport,
        )

    override fun createModuleDeserializer(
        moduleDescriptor: ModuleDescriptor,
        klib: KotlinLibrary?,
        strategyResolver: (String) -> DeserializationStrategy,
    ): IrModuleDeserializer {
        requireNotNull(klib) { "Expecting kotlin library" }
        val libraryAbiVersion = klib.versions.abiVersion ?: KotlinAbiVersion.CURRENT
        return LuaModuleDeserializer(moduleDescriptor, klib, strategyResolver, libraryAbiVersion)
    }

    override fun isBuiltInModule(moduleDescriptor: ModuleDescriptor): Boolean {
        val klib = (moduleDescriptor.klibModuleOrigin as? DeserializedKlibModuleOrigin)?.library ?: return false
        return klib.isLuaStdlib
    }

    private val deserializedFilesInKlibOrder = mutableMapOf<IrModuleFragment, List<IrFile>>()

    private inner class LuaModuleDeserializer(
        moduleDescriptor: ModuleDescriptor,
        klib: KotlinLibrary,
        strategyResolver: (String) -> DeserializationStrategy,
        libraryAbiVersion: KotlinAbiVersion,
    ) : BasicIrModuleDeserializer(this, moduleDescriptor, klib, strategyResolver, libraryAbiVersion) {
        init {
            deserializedFilesInKlibOrder[moduleFragment] = fileDeserializationStates.memoryOptimizedMap { it.file }
        }
    }

    fun getDeserializedFilesInKlibOrder(fragment: IrModuleFragment): List<IrFile> = deserializedFilesInKlibOrder[fragment] ?: emptyList()
}
