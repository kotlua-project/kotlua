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

import org.junit.jupiter.api.Test

class MultiFileLinkingBoxTest : AbstractKotluaTest() {
    @Test
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
