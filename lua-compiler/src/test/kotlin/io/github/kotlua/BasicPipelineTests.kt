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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.luaj.vm2.LuaValue

class BasicPipelineTests : AbstractKotluaTest() {
    @Test
    fun `arithmetic and unary operations compute expected values in Lua`() {
        val code =
            """
            fun test(a: Int, b: Int): Int {
                val sum = a + b
                val diff = sum - 3
                val prod = diff * 2
                val neg = -prod
                return neg + (+10)
            }
            """.trimIndent()

        // ((10 + 5) - 3) * 2 = 24 -> -24 + 10 = -14
        val (result, _) = evalLuaFunction(code, "test", LuaValue.valueOf(10), LuaValue.valueOf(5))
        assertEquals(-14, result.toint())
    }

    @Test
    fun `boolean logic and comparisons evaluate properly`() {
        val code =
            """
            fun test(a: Int, b: Int): Boolean {
                val cmp = (a < b) && (a <= 5) && (b >= 10) && (a != b)
                val eq = (a == 5) || false
                return !(!cmp) && eq
            }
            """.trimIndent()

        val (resTrue, _) = evalLuaFunction(code, "test", LuaValue.valueOf(5), LuaValue.valueOf(10))
        assertTrue(resTrue.toboolean())

        val (resFalse, _) = evalLuaFunction(code, "test", LuaValue.valueOf(6), LuaValue.valueOf(10))
        assertFalse(resFalse.toboolean())
    }

    @Test
    fun `local variables, reassignment, and nested local functions work`() {
        val code =
            """
            fun test(x: Int): Int {
                var acc = x
                fun addDouble(v: Int): Int {
                    return v * 2
                }
                acc = acc + addDouble(3)
                return acc
            }
            """.trimIndent()

        val (result, _) = evalLuaFunction(code, "test", LuaValue.valueOf(4))
        assertEquals(10, result.toint())
    }

    @Test
    fun `string concatenation, interpolation, and length work`() {
        val code =
            """
            fun test(name: String): String {
                val greeting = "Hello, " + name + "!"
                return "${'$'}greeting"
            }

            fun strLen(s: String): Int = s.length
            """.trimIndent()

        val luaCode = compileToLua(code)
        val (_, moduleExports) = loadLuaModule(luaCode)

        assertEquals("Hello, Kotlua!", moduleExports.get("test").call(LuaValue.valueOf("Kotlua")).tojstring())
        assertEquals(6, moduleExports.get("strLen").call(LuaValue.valueOf("Kotlua")).toint())
    }

    @Test
    fun `lua inline code captures local kotlin variables and remaps names across calls`() {
        val code =
            """
            fun compute(a: Int, b: Int): Int {
                val factor = 10
                val res: Int = lua("return a * factor + b")
                return res
            }

            fun test(): Int {
                return compute(2, 5) + compute(3, 1)
            }
            """.trimIndent()

        val luaCode = compileToLua(code)
        val (_, moduleExports) = loadLuaModule(luaCode)

        assertTrue(moduleExports.get("compute").isfunction(), "Expected 'compute' function in module exports:\n$luaCode")
        assertEquals(56, moduleExports.get("test").call().toint(), "Generated Lua:\n$luaCode")
    }
}
