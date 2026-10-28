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

import io.github.kotlua.cli.LuaCliDiagnostics.LUA_ARGUMENT_ERROR
import io.github.kotlua.cli.LuaCliDiagnostics.LUA_ARGUMENT_WARNING
import io.github.kotlua.cli.compiler.K2LuaCompilerArguments
import io.github.kotlua.config.includes
import io.github.kotlua.config.libraries
import io.github.kotlua.config.outputDir
import io.github.kotlua.config.outputName
import io.github.kotlua.ir.luaLowerings
import io.github.kotlua.platform.LuaPlatforms
import org.jetbrains.kotlin.backend.common.linkage.partial.setupPartialLinkageConfig
import org.jetbrains.kotlin.cli.common.*
import org.jetbrains.kotlin.cli.common.arguments.cliArgument
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.pipeline.AbstractConfigurationPhase
import org.jetbrains.kotlin.cli.pipeline.ArgumentsPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.CheckCompilationErrors
import org.jetbrains.kotlin.cli.pipeline.ConfigurationUpdater
import org.jetbrains.kotlin.cli.report
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import java.io.File
import java.io.IOException

object LuaConfigurationPhase : AbstractConfigurationPhase<K2LuaCompilerArguments>(
    name = "LuaConfigurationPhase",
    postActions = setOf(CheckCompilationErrors.CheckDiagnosticCollector),
    configurationUpdaters = listOf(LuaConfigurationUpdater),
) {
    override fun createMetadataVersion(versionArray: IntArray): BinaryVersion = MetadataVersion(*versionArray)
}

object LuaConfigurationUpdater : ConfigurationUpdater<K2LuaCompilerArguments>() {
    override fun fillConfiguration(
        input: ArgumentsPipelineArtifact<K2LuaCompilerArguments>,
        configuration: CompilerConfiguration,
    ) {
        val arguments = input.arguments
        initializeCommonConfiguration(configuration, arguments, input.rootDisposable)

        when (val outputName = arguments.moduleName) {
            null -> {
                configuration.report(
                    LUA_ARGUMENT_ERROR,
                    "IR: Specify output name via ${K2LuaCompilerArguments::moduleName.cliArgument}",
                )
            }

            else -> {
                configuration.outputName = outputName
            }
        }

        when (val outputDir = arguments.outputDir) {
            null -> {
                configuration.report(
                    LUA_ARGUMENT_ERROR,
                    "IR: Specify output dir via ${K2LuaCompilerArguments::outputDir.cliArgument}",
                )
            }

            else -> {
                try {
                    configuration.outputDir = File(outputDir).canonicalFile
                } catch (_: IOException) {
                    configuration.report(LUA_ARGUMENT_ERROR, "Could not resolve output directory")
                }
            }
        }

        configuration.targetPlatform = LuaPlatforms.defaultLuaPlatform
        configuration.phaseConfig =
            createPhaseConfig(arguments).also {
                if (arguments.listPhases && arguments.includes != null) {
                    it.list(luaLowerings)
                }
            }
    }

    private fun initializeCommonConfiguration(
        configuration: CompilerConfiguration,
        arguments: K2LuaCompilerArguments,
        rootDisposable: Disposable,
    ) {
        configuration.setupCommonKlibArguments(
            arguments = arguments,
            canBeMetadataKlibCompilation = false,
            rootDisposable = rootDisposable,
        )

        val libraries: List<String> = configureLibraries(arguments.libraries) + listOfNotNull(arguments.includes)
        val friendLibraries: List<String> = emptyList()

        configuration.checkForUnexpectedKlibLibraries(
            librariesToCheck = friendLibraries,
            librariesToCheckArgument = "",
            allLibraries = libraries,
            allLibrariesArgument = K2LuaCompilerArguments::libraries.cliArgument,
        )

        configuration.libraries += libraries
        arguments.includes?.let {
            configuration.includes = it
            configuration.prohibitExportKlibToOlderAbiVersionAtSecondStage()
        }

        val commonSourcesArray = arguments.commonSources
        val commonSources = commonSourcesArray.toSet()
        val hmppCliModuleStructure = configuration.hmppModuleStructure
        for (arg in arguments.freeArgs) {
            configuration.addKotlinSourceRoot(
                path = arg,
                isCommon = commonSources.contains(arg),
                hmppModuleName = hmppCliModuleStructure?.getModuleNameForSource(arg),
            )
        }

        val moduleName =
            arguments.moduleName ?: run {
                val message =
                    "Specify the module name via ${K2LuaCompilerArguments::moduleName.cliArgument}"
                configuration.report(LUA_ARGUMENT_ERROR, message)
                return
            }

        configuration.moduleName = moduleName
        configuration.perfManager?.targetDescription = moduleName
        configuration.allowKotlinPackage = arguments.allowKotlinPackage

        configuration.setupPartialLinkageConfig(arguments, LUA_ARGUMENT_WARNING, LUA_ARGUMENT_ERROR)
    }

    private fun configureLibraries(libraryString: String?): List<String> = libraryString?.splitByPathSeparator() ?: emptyList()

    private fun String.splitByPathSeparator(): List<String> =
        this
            .split(File.pathSeparator.toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()
            .filterNot { it.isEmpty() }
}
