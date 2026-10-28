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

import io.github.kotlua.fir.FirLuaSessionFactory
import io.github.kotlua.ir.loadLuaKlibs
import io.github.kotlua.library.LuaKlibPlatformChecker
import io.github.kotlua.platform.LuaPlatforms
import org.jetbrains.kotlin.CoreEnvironmentDeprecation
import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.cli.CliDiagnostics.COMPILER_ARGUMENTS_ERROR
import org.jetbrains.kotlin.cli.common.GroupedKtSources
import org.jetbrains.kotlin.cli.common.SessionConstructionUtils
import org.jetbrains.kotlin.cli.common.SessionWithSources
import org.jetbrains.kotlin.cli.common.allowNoSourceFiles
import org.jetbrains.kotlin.cli.common.collectSources
import org.jetbrains.kotlin.cli.common.diagnosticsCollector
import org.jetbrains.kotlin.cli.common.fileBelongsToModuleForLt
import org.jetbrains.kotlin.cli.common.fileBelongsToModuleForPsi
import org.jetbrains.kotlin.cli.common.isCommonSourceForLt
import org.jetbrains.kotlin.cli.common.isCommonSourceForPsi
import org.jetbrains.kotlin.cli.common.messages.SyntaxErrorReporter
import org.jetbrains.kotlin.cli.common.printVersion
import org.jetbrains.kotlin.cli.extensionsStorage
import org.jetbrains.kotlin.cli.hasMessageCollectorErrors
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.toVfsBasedProjectEnvironment
import org.jetbrains.kotlin.cli.pipeline.CheckCompilationErrors
import org.jetbrains.kotlin.cli.pipeline.ConfigurationPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.PerformanceNotifications
import org.jetbrains.kotlin.cli.pipeline.PipelinePhase
import org.jetbrains.kotlin.cli.report
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.moduleName
import org.jetbrains.kotlin.config.perfManager
import org.jetbrains.kotlin.config.useLightTree
import org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector
import org.jetbrains.kotlin.fir.DependencyListForCliModule
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.fir.pipeline.AllModulesFrontendOutput
import org.jetbrains.kotlin.fir.pipeline.SingleModuleFrontendOutput
import org.jetbrains.kotlin.fir.pipeline.buildResolveAndCheckFirFromKtFiles
import org.jetbrains.kotlin.fir.pipeline.buildResolveAndCheckFirViaLightTree
import org.jetbrains.kotlin.fir.pipeline.runPlatformCheckers
import org.jetbrains.kotlin.fir.session.AbstractFirKlibSessionFactory
import org.jetbrains.kotlin.fir.session.AbstractFirMetadataSessionFactory
import org.jetbrains.kotlin.fir.session.FirJsSessionFactory
import org.jetbrains.kotlin.fir.session.KlibIcData
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.util.PerformanceManager
import org.jetbrains.kotlin.utils.addToStdlib.shouldNotBeCalled

@OptIn(ExperimentalCompilerApi::class)
object LuaFrontendPipelinePhase : PipelinePhase<ConfigurationPipelineArtifact, LuaFrontendPipelineArtifact>(
    name = "LuaFrontendPipelinePhase",
    postActions = setOf(PerformanceNotifications.AnalysisFinished, CheckCompilationErrors.CheckDiagnosticCollector),
) {
    override fun executePhase(input: ConfigurationPipelineArtifact): LuaFrontendPipelineArtifact? {
        val configuration = input.configuration

        @OptIn(CoreEnvironmentDeprecation::class)
        val env =
            KotlinCoreEnvironment.createForProduction(
                projectDisposable = input.rootDisposable,
                configuration = configuration,
                configFiles = EnvironmentConfigFiles.METADATA_CONFIG_FILES,
            )

        val diagnosticsCollector = configuration.diagnosticsCollector
        val klibs = loadLuaKlibs(configuration, LuaKlibPlatformChecker)

        val extensionStorage = configuration.extensionsStorage ?: error("Extensions storage is not registered")

        val analyzedOutput =
            if (configuration.useLightTree) {
                val groupedSources =
                    collectSources(
                        compilerConfiguration = configuration,
                        projectEnvironment = env.toVfsBasedProjectEnvironment(),
                    )

                if (
                    groupedSources.isEmpty() &&
                    !configuration.allowNoSourceFiles
                ) {
                    if (!configuration.printVersion) {
                        configuration.report(COMPILER_ARGUMENTS_ERROR, "No source files")
                    }
                    return null
                }

                compileModulesToAnalyzedFirWithLightTree(
                    configuration = configuration,
                    resolvedLibraries = klibs.all,
                    groupedSources = groupedSources,
                    ktSourceFiles = groupedSources.commonSources + groupedSources.platformSources,
                    diagnosticsReporter = configuration.diagnosticsCollector,
                    performanceManager = configuration.perfManager,
                    extensionStorage = extensionStorage,
                )
            } else {
                val sourceFiles = env.getSourceFiles()
                if (
                    sourceFiles.isEmpty() &&
                    !configuration.allowNoSourceFiles
                ) {
                    if (!configuration.printVersion) {
                        configuration.report(COMPILER_ARGUMENTS_ERROR, "No source files")
                    }
                    return null
                }

                compileModuleToAnalyzedFirWithPsi(
                    configuration = configuration,
                    resolvedLibraries = klibs.all,
                    ktFiles = sourceFiles,
                    diagnosticsReporter = configuration.diagnosticsCollector,
                    extensionStorage = extensionStorage,
                )
            }

        return LuaFrontendPipelineArtifact(
            frontendOutput = analyzedOutput,
            configuration = configuration,
            resolvedLibraries = klibs.all,
            hasErrors = configuration.hasMessageCollectorErrors() || diagnosticsCollector.hasErrors,
        )
    }

    private fun compileModuleToAnalyzedFirWithPsi(
        configuration: CompilerConfiguration,
        resolvedLibraries: List<KotlinLibrary>,
        ktFiles: List<KtFile>,
        diagnosticsReporter: BaseDiagnosticsCollector,
        extensionStorage: CompilerPluginRegistrar.ExtensionStorage,
    ): AllModulesFrontendOutput {
        for (ktFile in ktFiles) {
            SyntaxErrorReporter.reportSyntaxErrors(ktFile, diagnosticsReporter)
        }
        val output =
            compileModuleToAnalyzedFir(
                configuration = configuration,
                resolvedLibraries = resolvedLibraries,
                files = ktFiles,
                extensionStorage = extensionStorage,
                isCommonSource = isCommonSourceForPsi,
                fileBelongsToModule = fileBelongsToModuleForPsi,
                buildResolveAndCheckFir = { session, files ->
                    buildResolveAndCheckFirFromKtFiles(session, files, diagnosticsReporter)
                },
            )
        output.runPlatformCheckers(diagnosticsReporter)
        return AllModulesFrontendOutput(output)
    }

    private fun compileModulesToAnalyzedFirWithLightTree(
        configuration: CompilerConfiguration,
        resolvedLibraries: List<KotlinLibrary>,
        groupedSources: GroupedKtSources,
        ktSourceFiles: List<KtSourceFile>,
        diagnosticsReporter: BaseDiagnosticsCollector,
        performanceManager: PerformanceManager?,
        extensionStorage: CompilerPluginRegistrar.ExtensionStorage,
    ): AllModulesFrontendOutput {
        val output =
            compileModuleToAnalyzedFir(
                configuration = configuration,
                resolvedLibraries = resolvedLibraries,
                files = ktSourceFiles,
                extensionStorage = extensionStorage,
                isCommonSource = { groupedSources.isCommonSourceForLt(it) },
                fileBelongsToModule = { file, it -> groupedSources.fileBelongsToModuleForLt(file, it) },
                buildResolveAndCheckFir = { session, files ->
                    buildResolveAndCheckFirViaLightTree(
                        session = session,
                        ktFiles = files,
                        diagnosticsReporter = diagnosticsReporter,
                        countFilesAndLines = performanceManager?.let { it::addSourcesStats },
                    )
                },
            )
        output.runPlatformCheckers(diagnosticsReporter)
        return AllModulesFrontendOutput(output)
    }

    private inline fun <F> compileModuleToAnalyzedFir(
        configuration: CompilerConfiguration,
        resolvedLibraries: List<KotlinLibrary>,
        files: List<F>,
        extensionStorage: CompilerPluginRegistrar.ExtensionStorage,
        noinline isCommonSource: (F) -> Boolean,
        noinline fileBelongsToModule: (F, String) -> Boolean,
        buildResolveAndCheckFir: (FirSession, List<F>) -> SingleModuleFrontendOutput,
    ): List<SingleModuleFrontendOutput> {
        @Suppress("UNCHECKED_CAST")
        val extensionRegistrars = extensionStorage[FirExtensionRegistrarAdapter] as List<FirExtensionRegistrar>

        val mainModuleName = configuration.moduleName!!
        val escapedMainModuleName = Name.special("<$mainModuleName>")
        val dependencyList = DependencyListForCliModule.build(escapedMainModuleName)

        val sessionWithSources =
            prepareLuaSessions(
                files = files,
                configuration = configuration,
                rootModuleName = escapedMainModuleName,
                resolvedLibraries = resolvedLibraries,
                libraryList = dependencyList,
                extensionRegistrars = extensionRegistrars,
                isCommonSource = isCommonSource,
                fileBelongsToModule = fileBelongsToModule,
            )

        return sessionWithSources.map {
            buildResolveAndCheckFir(it.session, it.files)
        }
    }
}

private fun <F> prepareLuaSessions(
    files: List<F>,
    configuration: CompilerConfiguration,
    rootModuleName: Name,
    resolvedLibraries: List<KotlinLibrary>,
    libraryList: DependencyListForCliModule,
    extensionRegistrars: List<FirExtensionRegistrar>,
    isCommonSource: (F) -> Boolean,
    fileBelongsToModule: (F, String) -> Boolean,
    icData: KlibIcData? = null,
    sessionFactory: AbstractFirKlibSessionFactory<*> = FirLuaSessionFactory,
): List<SessionWithSources<F>> =
    SessionConstructionUtils.prepareSessions(
        files = files,
        configuration = configuration,
        rootModuleName = rootModuleName,
        targetPlatform = LuaPlatforms.defaultLuaPlatform,
        metadataCompilationMode = false,
        libraryList = libraryList,
        extensionRegistrars = extensionRegistrars,
        isCommonSource = isCommonSource,
        isScript = { false },
        fileBelongsToModule = fileBelongsToModule,
        createMetadataSessionFactoryContextForHmppCommonLibrarySession = {
            // TODO
            AbstractFirMetadataSessionFactory.Context(
                createJvmContext = { shouldNotBeCalled() },
                createJsContext = { FirJsSessionFactory.Context(configuration) },
            )
        },
        createSharedLibrarySession = {
            sessionFactory.createSharedLibrarySession(
                rootModuleName,
                configuration,
                extensionRegistrars,
            )
        },
        createLibrarySession = { sharedLibrarySession ->
            sessionFactory.createLibrarySession(
                resolvedLibraries,
                sharedLibrarySession,
                libraryList.moduleDataProvider,
                extensionRegistrars,
                configuration,
            )
        },
        createSourceSession = { moduleData, kmpModuleKind, sessionConfigurator ->
            sessionFactory.createSourceSession(
                moduleData,
                extensionRegistrars,
                configuration,
                kmpModuleKind,
                icData = icData,
                init = sessionConfigurator,
            )
        },
        additionalProvidersForMetadataLibrarySessionsInHmppMode = { session, moduleDataProvider, scopeProvider, libraries ->
            sessionFactory.createAdditionalDependencyProviders(session, moduleDataProvider, scopeProvider, libraries)
        },
    )
