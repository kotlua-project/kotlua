package io.github.kotlua

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class PropertyInitializationOrderBoxTest : AbstractKotluaTest() {
    @Test
    @Disabled("TODO: IrField / IrGetField / IrSetField + lazy _init_properties")
    fun `top-level properties initialize strictly top-to-bottom even if read in reverse order`() {
        assertBoxOk(
            """
            var log: String = ""

            fun record(tag: String): String {
                log = log + tag
                return tag
            }

            val first: String = record("A")
            val second: String = record("B")

            fun box(): String {
                val b = second
                val a = first
                return if (a == "A" && b == "B" && log == "AB") "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }
}