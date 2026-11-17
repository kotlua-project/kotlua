package io.github.kotlua

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class LocalFunctionScopeBoxTest : AbstractKotluaTest() {
    @Test
    @Disabled("TODO: Verify local function self-recursion works under local declaration style")
    fun `recursive local function and shadowed local functions in sibling blocks`() {
        assertBoxOk(
            """
            fun sumDown(n: Int): Int {
                fun rec(k: Int): Int = if (k <= 0) 0 else k + rec(k - 1)
                return rec(n)
            }

            fun box(): String {
                val a = if (true) {
                    fun dup(): String = "O"
                    dup()
                } else ""

                val b = if (true) {
                    fun dup(): String = "K"
                    dup()
                } else ""

                return if (sumDown(5) == 15) a + b else "FAIL"
            }
            """.trimIndent(),
        )
    }
}