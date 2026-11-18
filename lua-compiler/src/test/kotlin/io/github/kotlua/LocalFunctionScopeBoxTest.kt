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

class LocalFunctionScopeBoxTest : AbstractKotluaTest() {
    @Test
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
