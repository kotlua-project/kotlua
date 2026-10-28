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
package io.github.kotlua.cli.compiler

import io.github.kotlua.cli.pipeline.LuaCliPipeline
import io.github.kotlua.platform.LuaPlatforms
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.platform.TargetPlatform

class K2LuaCompiler : CLICompiler<K2LuaCompilerArguments>() {
    override val platform: TargetPlatform = LuaPlatforms.defaultLuaPlatform

    override fun executableScriptFileName(): String = "kotlinc-lua"

    override fun createArguments(): K2LuaCompilerArguments = K2LuaCompilerArguments()

    override fun createMetadataVersion(versionArray: IntArray): BinaryVersion = MetadataVersion(*versionArray)

    override fun doExecutePhased(
        arguments: K2LuaCompilerArguments,
        services: Services,
        basicMessageCollector: MessageCollector,
    ): ExitCode =
        LuaCliPipeline(defaultPerformanceManager).execute(
            arguments,
            services,
            basicMessageCollector,
        )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            doMain(K2LuaCompiler(), args)
        }
    }
}
