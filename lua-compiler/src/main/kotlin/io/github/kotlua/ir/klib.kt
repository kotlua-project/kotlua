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
package io.github.kotlua.ir

import io.github.kotlua.ir.lower.serialization.ir.LuaIrFileMetadata
import io.github.kotlua.ir.lower.serialization.ir.LuaIrLinker
import io.github.kotlua.ir.lower.serialization.ir.LuaIrModuleSerializer
import io.github.kotlua.ir.lower.serialization.ir.LuaManglerDesc
import io.github.kotlua.ir.lower.serialization.ir.LuaModulesStructure
import io.github.kotlua.library.isLuaStdlib
import io.github.kotlua.library.metadata.LuaKlibMetadataModuleDescriptorFactory
import io.github.kotlua.library.writer.LuaKlibWriter
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.backend.common.IrBuiltInsForLinker
import org.jetbrains.kotlin.backend.common.IrModuleDependencies
import org.jetbrains.kotlin.backend.common.IrModuleInfo
import org.jetbrains.kotlin.backend.common.LoadedKlibs
import org.jetbrains.kotlin.backend.common.klibAbiVersionForManifest
import org.jetbrains.kotlin.backend.common.linkage.partial.partialLinkageConfig
import org.jetbrains.kotlin.backend.common.serialization.DeserializationStrategy
import org.jetbrains.kotlin.backend.common.serialization.IrSerializationSettings
import org.jetbrains.kotlin.backend.common.serialization.KotlinFileSerializedData
import org.jetbrains.kotlin.backend.common.serialization.addLanguageFeaturesToManifest
import org.jetbrains.kotlin.backend.common.serialization.metadata.DynamicTypeDeserializer
import org.jetbrains.kotlin.backend.common.serialization.metadata.KlibSingleFileMetadataSerializer
import org.jetbrains.kotlin.backend.common.serialization.serializeModuleIntoKlib
import org.jetbrains.kotlin.backend.common.serialization.signature.IdSignatureDescriptor
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.cli.common.diagnosticsCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.InternalSymbolFinderAPI
import org.jetbrains.kotlin.ir.IrBuiltIns
import org.jetbrains.kotlin.ir.IrDiagnosticReporter
import org.jetbrains.kotlin.ir.KtDiagnosticReporterWithImplicitIrBasedContext
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.ExternalDependenciesGenerator
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.konan.properties.Properties
import org.jetbrains.kotlin.library.KlibFormat
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.KotlinLibraryVersioning
import org.jetbrains.kotlin.library.isAnyPlatformStdlib
import org.jetbrains.kotlin.library.metadata.KlibMetadataFactories
import org.jetbrains.kotlin.library.uniqueName
import org.jetbrains.kotlin.library.writer.includeIr
import org.jetbrains.kotlin.library.writer.includeMetadata
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.util.PerformanceManager
import org.jetbrains.kotlin.util.PhaseType
import org.jetbrains.kotlin.util.metadataVersion
import org.jetbrains.kotlin.util.tryMeasurePhaseTime
import java.nio.file.Path
import kotlin.collections.emptyList

private fun createBuiltIns(storageManager: StorageManager) = object : KotlinBuiltIns(storageManager) {}

@OptIn(K1Deprecation::class)
val LuaFactories = KlibMetadataFactories(::createBuiltIns, DynamicTypeDeserializer)

val KlibMetadataFactories.LuaDeserializedDescriptorFactory: LuaKlibMetadataModuleDescriptorFactory
    get() = LuaKlibMetadataModuleDescriptorFactory(DefaultDeserializedDescriptorFactory)

fun loadIr(
    modulesStructure: LuaModulesStructure,
    irFactory: IrFactory,
): IrModuleInfo {
    val configuration = modulesStructure.compilerConfiguration

    val signaturer = IdSignatureDescriptor(LuaManglerDesc)
    val symbolTable = SymbolTable(signaturer, irFactory)

    val mainModuleLib =
        modulesStructure.klibs.included
            ?: error("No module with ${modulesStructure.mainModulePath} found")
    val moduleDescriptor = modulesStructure.getModuleDescriptor(mainModuleLib)
    val friendModules = mapOf(mainModuleLib.uniqueName to modulesStructure.klibs.friends.map { it.uniqueName })

    return getIrModuleInfoForKlib(
        moduleDescriptor = moduleDescriptor,
        klibs = modulesStructure.klibs,
        friendModules = friendModules,
        configuration = configuration,
        symbolTable = symbolTable,
    ) { modulesStructure.getModuleDescriptor(it) }
}

private fun getIrModuleInfoForKlib(
    moduleDescriptor: ModuleDescriptor,
    klibs: LoadedKlibs,
    friendModules: Map<String, List<String>>,
    configuration: CompilerConfiguration,
    symbolTable: SymbolTable,
    mapping: (KotlinLibrary) -> ModuleDescriptor,
): IrModuleInfo {
    val irDiagnosticReporter =
        KtDiagnosticReporterWithImplicitIrBasedContext(
            diagnosticReporter = configuration.diagnosticsCollector,
            configuration.languageVersionSettings,
        )

    val irLinker =
        LuaIrLinker(
            configuration = configuration,
            symbolTable = symbolTable,
            partialLinkageConfig = configuration.partialLinkageConfig,
            irDiagnosticReporter = irDiagnosticReporter,
            friendModules = friendModules,
        )

    val moduleDependencies: IrModuleDependencies =
        deserializeDependencies(
            klibs = klibs,
            irLinker = irLinker,
            filesToLoad = null,
            mapping = mapping,
        )

    irLinker.init(null)

    @OptIn(InternalSymbolFinderAPI::class)
    val irBuiltIns = IrBuiltInsForLinker(irLinker, configuration.languageVersionSettings)

    ExternalDependenciesGenerator(symbolTable, listOf(irLinker)).generateUnboundSymbolsAsDependencies()
    irLinker.postProcess(irBuiltIns, inOrAfterLinkageStep = true)

    return IrModuleInfo(
        module = moduleDependencies.included!!,
        dependencies = moduleDependencies,
        bultins = irBuiltIns,
        symbolTable = symbolTable,
        deserializer = irLinker,
    )
}

private fun deserializeDependencies(
    klibs: LoadedKlibs,
    irLinker: LuaIrLinker,
    filesToLoad: Set<String>?,
    mapping: (KotlinLibrary) -> ModuleDescriptor,
): IrModuleDependencies {
    val all = mutableListOf<IrModuleFragment>()
    var stdlib: IrModuleFragment? = null
    var included: IrModuleFragment? = null

    klibs.all.forEach { klib: KotlinLibrary ->
        val descriptor: ModuleDescriptor = mapping(klib)
        val module: IrModuleFragment =
            when {
                klibs.included == null -> {
                    irLinker.deserializeIrModuleHeader(
                        moduleDescriptor = descriptor,
                        kotlinLibrary = klib,
                        deserializationStrategy = { DeserializationStrategy.EXPLICITLY_EXPORTED },
                    )
                }

                filesToLoad != null && klib == klibs.included -> {
                    irLinker.deserializeDirtyFiles(
                        moduleDescriptor = descriptor,
                        kotlinLibrary = klib,
                        dirtyFiles = filesToLoad,
                    )
                }

                filesToLoad != null && klib != klibs.included -> {
                    irLinker.deserializeHeadersWithInlineBodies(
                        moduleDescriptor = descriptor,
                        kotlinLibrary = klib,
                    )
                }

                klib == klibs.included -> {
                    irLinker.deserializeIrModuleHeader(
                        moduleDescriptor = descriptor,
                        kotlinLibrary = klib,
                        deserializationStrategy = { DeserializationStrategy.ALL },
                    )
                }

                else -> {
                    irLinker.deserializeIrModuleHeader(
                        moduleDescriptor = descriptor,
                        kotlinLibrary = klib,
                        deserializationStrategy = { DeserializationStrategy.EXPLICITLY_EXPORTED },
                    )
                }
            }

        all += module

        when {
            klib.isAnyPlatformStdlib || klib.isLuaStdlib -> stdlib = module
            klib == klibs.included -> included = module
        }
    }

    return IrModuleDependencies(
        all = all,
        stdlib = stdlib,
        included = included,
    )
}

fun serializeModuleIntoKlib(
    moduleName: String,
    configuration: CompilerConfiguration,
    diagnosticReporter: IrDiagnosticReporter,
    metadataSerializer: KlibSingleFileMetadataSerializer<*>,
    klibPath: Path,
    moduleFragment: IrModuleFragment,
    irBuiltIns: IrBuiltIns,
    cleanFiles: List<KotlinFileSerializedData>,
    nopack: Boolean,
    performanceManager: PerformanceManager? = null,
) {
    val versions =
        KotlinLibraryVersioning(
            compilerVersion = KotlinCompilerVersion.VERSION,
            abiVersion = configuration.klibAbiVersionForManifest(),
            metadataVersion = configuration.metadataVersion(),
        )

    val serializerOutput =
        performanceManager.tryMeasurePhaseTime(PhaseType.IrSerialization) {
            serializeModuleIntoKlib(
                moduleName = moduleFragment.name.asString(),
                irModuleFragment = moduleFragment,
                configuration = configuration,
                diagnosticReporter = diagnosticReporter,
                cleanFiles = cleanFiles,
                dependencies = emptyList(),
                createModuleSerializer = { irDiagnosticReporter ->
                    LuaIrModuleSerializer(
                        settings = IrSerializationSettings(configuration),
                        diagnosticReporter = irDiagnosticReporter,
                        irBuiltIns = irBuiltIns,
                    ) { LuaIrFileMetadata() }
                },
                metadataSerializer = metadataSerializer,
                processCompiledFileData = null,
                processKlibHeader = {},
            )
        }

    val fullSerializedIr = serializerOutput.serializedIr

    val properties =
        Properties().also { p ->
            addLanguageFeaturesToManifest(p, configuration.languageVersionSettings)
        }

    performanceManager.tryMeasurePhaseTime(PhaseType.KlibWriting) {
        LuaKlibWriter {
            format(if (nopack) KlibFormat.Directory else KlibFormat.ZipArchive)
            manifest {
                moduleName(moduleName)
                versions(versions)
                customProperties { this += properties }
            }
            includeMetadata(serializerOutput.serializedMetadata ?: error("expected serialized metadata"))
            fullSerializedIr?.let {
                includeIr(fullSerializedIr)
            }
        }.writeTo(klibPath)
    }
}
