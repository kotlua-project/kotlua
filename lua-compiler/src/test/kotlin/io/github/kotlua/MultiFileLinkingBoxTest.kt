package io.github.kotlua

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class MultiFileLinkingBoxTest : AbstractKotluaTest() {
    @Test
    @Disabled("TODO: Multi-file compilation + cyclic references between files")
    fun `cyclic calls across two files in the same module`() {
        assertBoxOk(
            mapOf(
                "Ping.kt" to
                    """
                    fun ping(n: Int): Int =
                        if (n <= 0) 0 else pong(n - 1) + 1
                    """.trimIndent(),
                "Pong.kt" to
                    """
                    fun pong(n: Int): Int =
                        if (n <= 0) 0 else ping(n - 1) + 1

                    fun box(): String =
                        if (ping(6) == 6) "OK" else "FAIL"
                    """.trimIndent(),
            ),
        )
    }
}