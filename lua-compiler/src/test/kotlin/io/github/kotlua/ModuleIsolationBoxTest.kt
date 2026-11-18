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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.luaj.vm2.lib.jse.JsePlatform

class ModuleIsolationBoxTest : AbstractKotluaTest() {
    @Test
    fun `kotlin functions named like lua builtins do not overwrite _G`() {
        val luaCode =
            compileToLua(
                """
                fun type(): String = "kotlin_type"
                fun tostring(): String = "kotlin_tostring"

                fun box(): String {
                    val nativeType = lua("type(123)")
                    return if (type() == "kotlin_type" && tostring() == "kotlin_tostring" && nativeType == "number") {
                        "OK"
                    } else {
                        "FAIL"
                    }
                }
                """.trimIndent(),
            )

        val globals = JsePlatform.standardGlobals()
        val exported = globals.load(luaCode, "testModule.lua").call()

        assertEquals("function", globals.get("type").typename(), "_G.type was overwritten!\n$luaCode")
        assertEquals("function", globals.get("tostring").typename(), "_G.tostring was overwritten!\n$luaCode")
        val boxFn = if (exported.istable()) exported.get("box") else globals.get("box")
        assertEquals("OK", boxFn.call().tojstring(), "Generated Lua:\n$luaCode")
    }

    @Test
    fun `private declarations are not exported in module table and not leaked to _G`() {
        val luaCode =
            compileToLua(
                """
                private fun secret(): String = "OK"

                fun box(): String = secret()
                """.trimIndent(),
            )

        val globals = JsePlatform.standardGlobals()
        val exported = globals.load(luaCode, "testModule.lua").call()

        assertTrue(globals.get("secret").isnil(), "private fun leaked into _G!\n$luaCode")
        assertTrue(exported.istable(), "Module must return an export table!\n$luaCode")
        assertTrue(exported.get("secret").isnil(), "private fun leaked into module export table!\n$luaCode")
        assertEquals("OK", exported.get("box").call().tojstring())
    }

    @Test
    fun `same private function names in different files do not collide`() {
        assertBoxOk(
            mapOf(
                "A.kt" to
                    """
                    package test

                    private fun helper(): String = "O"
                    fun partA(): String = helper()
                    """.trimIndent(),
                "B.kt" to
                    """
                    package test

                    private fun helper(): String = "K"
                    fun partB(): String = helper()

                    fun box(): String = partA() + partB()
                    """.trimIndent(),
            ),
        )
    }
}
