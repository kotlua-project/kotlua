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
package io.github.kotlua

import io.github.kotlua.cli.compiler.K2LuaCompiler
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.io.TempDir
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

abstract class AbstractKotluaTest {
    @TempDir
    lateinit var tempDir: Path

    protected val stdlibPath: String
        get() =
            System.getProperty("kotlua.test.stdlib")
                ?: error(
                    "System property 'kotlua.test.stdlib' is not set. " +
                        "Run via Gradle :lua-compiler:test or pass -Dkotlua.test.stdlib=...",
                )

    protected val nativeLuaExecutable: String?
        get() = System.getProperty("kotlua.test.luaExecutable")?.takeIf { it.isNotBlank() }

    protected fun compileToLua(
        kotlinCode: String,
        moduleName: String = "testModule",
    ): String = compileToLua(mapOf("Test.kt" to kotlinCode), moduleName)

    protected fun compileToLua(
        files: Map<String, String>,
        moduleName: String = "testModule",
    ): String {
        val srcDir = tempDir.resolve("src").createDirectories()
        val classesDir = tempDir.resolve("classes").createDirectories()
        val generatedDir = tempDir.resolve("generated").createDirectories()

        val ktPaths =
            files.map { (relativePath, content) ->
                val target = srcDir.resolve(relativePath)
                target.parent?.createDirectories()
                target.writeText(content)
                target.absolutePathString()
            }

        runCompiler(
            stageName = "compile",
            args =
                arrayOf(
                    "-libraries",
                    stdlibPath,
                    "-ir-output-name",
                    moduleName,
                    "-nopack",
                    "-ir-output-dir",
                    classesDir.absolutePathString(),
                    *ktPaths.toTypedArray(),
                ),
        )

        runCompiler(
            stageName = "link",
            args =
                arrayOf(
                    "-Xinclude=${classesDir.absolutePathString()}",
                    "-libraries",
                    stdlibPath,
                    "-ir-output-name",
                    moduleName,
                    "-nopack",
                    "-ir-output-dir",
                    generatedDir.absolutePathString(),
                ),
        )

        val luaFile =
            generatedDir.resolve("$moduleName.lua").takeIf { Files.exists(it) }
                ?: Files.walk(generatedDir).use { stream ->
                    stream
                        .filter { it.isRegularFile() && it.extension == "lua" }
                        .findFirst()
                        .orElseThrow { AssertionError("No .lua output file found in $generatedDir") }
                }

        return luaFile.readText()
    }

    protected fun evalLuaFunction(
        kotlinCode: String,
        functionName: String = "test",
        vararg args: LuaValue,
    ): Pair<LuaValue, String> = evalLuaFunction(mapOf("Test.kt" to kotlinCode), functionName, *args)

    protected fun evalLuaFunction(
        files: Map<String, String>,
        functionName: String = "test",
        vararg args: LuaValue,
    ): Pair<LuaValue, String> {
        val luaCode = compileToLua(files)
        val executable = nativeLuaExecutable
        if (executable != null) {
            return evalOnNativeLua(executable, luaCode, functionName, args) to luaCode
        }

        val (globals, moduleExports) = loadLuaModule(luaCode)
        val fn =
            if (moduleExports.istable()) {
                moduleExports.get(functionName)
            } else {
                globals.get(functionName)
            }
        assertFalse(fn.isnil(), "Function '$functionName' was not found in generated Lua:\n$luaCode")

        val result =
            try {
                fn.invoke(args).arg1()
            } catch (e: Throwable) {
                fail("Runtime error calling '$functionName': ${e.message}\n\nGenerated Lua:\n$luaCode", e)
            }
        return result to luaCode
    }

    private fun evalOnNativeLua(
        executable: String,
        luaCode: String,
        functionName: String,
        args: Array<out LuaValue>,
    ): LuaValue {
        val moduleFile = tempDir.resolve("native_module.lua").apply { writeText(luaCode) }
        val resultFile = tempDir.resolve("native_result.txt")
        val luaArgs = args.joinToString(", ") { it.toLuaLiteral() }
        val escapedModulePath = moduleFile.absolutePathString().replace("\\", "/")
        val escapedResultPath = resultFile.absolutePathString().replace("\\", "/")
        val luaModulesDir = tempDir.resolve("lua_modules")
        val packagePathSetup =
            if (Files.exists(luaModulesDir)) {
                val escapedLuaDir = luaModulesDir.absolutePathString().replace("\\", "/").escapeLua()
                """package.path = "$escapedLuaDir/?.lua;$escapedLuaDir/?/init.lua;" .. package.path"""
            } else {
                ""
            }

        val runnerScript =
            """
            $packagePathSetup    
            local chunk = assert(loadfile("${escapedModulePath.escapeLua()}"))
            local exported = chunk()
            local fn = type(exported) == "table" and exported["${functionName.escapeLua()}"] or _G["${functionName.escapeLua()}"]
            assert(fn ~= nil, "Function '${functionName.escapeLua()}' was not found")
            local res = fn($luaArgs)
            local out = assert(io.open("${escapedResultPath.escapeLua()}", "wb"))
            out:write(type(res) .. "\n" .. tostring(res))
            out:close()
            """.trimIndent()

        val runnerFile = tempDir.resolve("native_runner.lua").apply { writeText(runnerScript) }
        val process =
            ProcessBuilder(executable, runnerFile.absolutePathString())
                .redirectErrorStream(true)
                .start()
        val processOutput = process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        val exitCode = process.waitFor()
        assertEquals(
            0,
            exitCode,
            "Native Lua '$executable' failed (exit=$exitCode):\n$processOutput\n\nGenerated Lua:\n$luaCode",
        )

        val lines = resultFile.readText().split('\n', limit = 2)
        val type = lines[0]
        val raw = lines.getOrElse(1) { "" }
        return when (type) {
            "nil" -> LuaValue.NIL
            "boolean" -> LuaValue.valueOf(raw == "true")
            "number" -> raw.toIntOrNull()?.let { LuaValue.valueOf(it) } ?: LuaValue.valueOf(raw.toDouble())
            else -> LuaValue.valueOf(raw)
        }
    }

    protected fun writeLuaModules(luaFiles: Map<String, String>) {
        val luaDir = tempDir.resolve("lua_modules").createDirectories()
        for ((relativePath, content) in luaFiles) {
            val target = luaDir.resolve(relativePath)
            target.parent?.createDirectories()
            target.writeText(content)
        }
    }

    protected fun assertBoxOk(
        kotlinCode: String,
        luaFiles: Map<String, String>,
    ) {
        writeLuaModules(luaFiles)
        assertBoxOk(kotlinCode)
    }

    protected fun assertBoxOk(
        files: Map<String, String>,
        luaFiles: Map<String, String>,
    ) {
        writeLuaModules(luaFiles)
        assertBoxOk(files)
    }

    protected fun assertBoxOk(kotlinCode: String) {
        assertBoxOk(mapOf("Test.kt" to kotlinCode))
    }

    protected fun assertBoxOk(files: Map<String, String>) {
        val (result, luaCode) = evalLuaFunction(files, "box")
        assertEquals("OK", result.tojstring(), "box() failed. Generated Lua:\n$luaCode")
    }

    protected fun loadLuaGlobals(
        luaCode: String,
        chunkName: String = "testModule.lua",
    ): Globals {
        val globals = JsePlatform.standardGlobals()
        try {
            globals.load(luaCode, chunkName).call()
        } catch (e: Throwable) {
            fail<Unit>("Lua load/execution error: ${e.message}\n\nGenerated Lua:\n$luaCode", e)
        }
        return globals
    }

    protected fun loadLuaModule(
        luaCode: String,
        chunkName: String = "testModule.lua",
    ): Pair<Globals, LuaValue> {
        val globals = JsePlatform.standardGlobals()
        val moduleExports =
            try {
                val luaModulesDir = tempDir.resolve("lua_modules")
                if (Files.exists(luaModulesDir)) {
                    val escapedLuaDir = luaModulesDir.absolutePathString().replace("\\", "/").escapeLua()
                    globals.load("""package.path = "$escapedLuaDir/?.lua;$escapedLuaDir/?/init.lua;" .. package.path""").call()
                }
                globals.load(luaCode, chunkName).call()
            } catch (e: Throwable) {
                fail("Lua load/execution error: ${e.message}\n\nGenerated Lua:\n$luaCode", e)
            }
        return globals to moduleExports
    }

    private fun runCompiler(
        stageName: String,
        args: Array<String>,
    ) {
        val outputBuffer = ByteArrayOutputStream()
        val errStream = PrintStream(outputBuffer, true, StandardCharsets.UTF_8)

        val exitCode = K2LuaCompiler().exec(errStream, MessageRenderer.PLAIN_FULL_PATHS, *args)
        val compilerOutput = outputBuffer.toString(StandardCharsets.UTF_8)

        assertEquals(
            ExitCode.OK,
            exitCode,
            "K2LuaCompiler failed at '$stageName' stage with $exitCode.\nCompiler output:\n$compilerOutput",
        )
    }

    private fun LuaValue.toLuaLiteral(): String =
        when {
            isnil() -> {
                "nil"
            }

            isboolean() -> {
                toboolean().toString()
            }

            isint() -> {
                toint().toString()
            }

            isnumber() -> {
                val d = todouble()
                when {
                    d.isNaN() -> "(0/0)"
                    d == Double.POSITIVE_INFINITY -> "(1/0)"
                    d == Double.NEGATIVE_INFINITY -> "(-1/0)"
                    else -> d.toString()
                }
            }

            else -> {
                "\"${tojstring().escapeLua()}\""
            }
        }

    private fun String.escapeLua(): String =
        buildString(length) {
            for (ch in this@escapeLua) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }

    companion object {
        init {
            System.setProperty("java.awt.headless", "true")
            setupIdeaStandaloneExecution()
        }
    }
}
