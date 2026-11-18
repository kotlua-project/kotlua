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

class IntegerDivisionAndRemBoxTest : AbstractKotluaTest() {
    @Test
    fun `integer division truncates toward zero for positive and negative operands`() {
        assertBoxOk(
            """
            fun box(): String {
                val ok =
                    (5 / 2 == 2) &&
                        (-5 / 2 == -2) &&
                        (5 / -2 == -2) &&
                        (-5 / -2 == 2) &&
                        (1 / 2 == 0) &&
                        (-1 / 2 == 0)
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `mixed int and double division keeps floating point result`() {
        assertBoxOk(
            """
            fun box(): String {
                val a = 5
                val b = 2.0
                val ok = (5.0 / 2.0 == 2.5) && (a / b == 2.5) && (b / a == 0.4)
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `integer division results behave as single values in multi-argument calls`() {
        assertBoxOk(
            """
            fun sumTwo(a: Int, b: Int): Int = a + b

            fun box(): String {
                val res = sumTwo(7 / 2, 9 / 2)
                return if (res == 7) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `remainder keeps dividend sign for negative integers`() {
        assertBoxOk(
            """
            fun box(): String {
                val ok =
                    (7 % 3 == 1) &&
                        (-7 % 3 == -1) &&
                        (7 % -3 == 1) &&
                        (-7 % -3 == -1)
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `remainder keeps dividend sign for negative doubles`() {
        assertBoxOk(
            """
            fun box(): String {
                val x = -5.5
                val y = 2.0
                return if (x % y == -1.5) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }
}
