package io.github.kotlua

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.luaj.vm2.lib.jse.JsePlatform

class ModuleIsolationBoxTest : AbstractKotluaTest() {
    @Test
    @Disabled("TODO: Module scoping (_M / _P) instead of polluting _G")
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
    @Disabled("TODO: Module export table + exclude private declarations")
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
    @Disabled("TODO: File/module scoping for private functions with same name")
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