package io.github.kotlua

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class NameManglingBoxTest : AbstractKotluaTest() {
    @Test
    @Disabled("TODO: LuaNameLinkingNamer (overloads mangling)")
    fun `overloaded functions do not overwrite each other`() {
        assertBoxOk(
            """
            fun pick(x: Int): String = "int"
            fun pick(x: String): String = "str"

            fun box(): String {
                val res = pick(1) + pick("a")
                return if (res == "intstr") "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    @Disabled("TODO: LuaNameLinkingNamer (package-qualified naming)")
    fun `same function names in different packages do not collide`() {
        assertBoxOk(
            mapOf(
                "pkg1/One.kt" to
                    """
                    package pkg1
                    fun tag(): String = "O"
                    """.trimIndent(),
                "pkg2/Two.kt" to
                    """
                    package pkg2
                    fun tag(): String = "K"
                    """.trimIndent(),
                "Main.kt" to
                    """
                    fun box(): String = pkg1.tag() + pkg2.tag()
                    """.trimIndent(),
            ),
        )
    }
}