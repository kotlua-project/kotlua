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

class PropertyInitializationOrderBoxTest : AbstractKotluaTest() {
    @Test
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
