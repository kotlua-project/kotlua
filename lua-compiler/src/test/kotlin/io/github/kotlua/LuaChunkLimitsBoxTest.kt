package io.github.kotlua

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class LuaChunkLimitsBoxTest : AbstractKotluaTest() {
    @Test
    @Disabled("TODO: Ensure module tables / chunk splitting do not hit Lua 200-locals or 60-upvalues limit")
    fun `more than 250 top-level functions and properties do not exceed Lua local or upvalue limits`() {
        val count = 250
        val code =
            buildString {
                appendLine("fun f0(): Int = 1")
                for (i in 1 until count) {
                    appendLine("fun f$i(): Int = f${i - 1}() + 1")
                }
                for (i in 0 until count) {
                    appendLine("val v$i: Int = f$i()")
                }
                appendLine(
                    """
                    fun box(): String {
                        val ok = v0 == 1 && v${count - 1} == $count && f${count - 1}() == $count
                        return if (ok) "OK" else "FAIL"
                    }
                    """.trimIndent(),
                )
            }

        assertBoxOk(code)
    }
}