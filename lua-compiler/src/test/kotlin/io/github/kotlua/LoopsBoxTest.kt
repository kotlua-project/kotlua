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

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class LoopsBoxTest : AbstractKotluaTest() {
    @Test
    @Disabled("TODO: while loop with break and continue emulation in Lua 5.1")
    fun `while loop with break and continue`() {
        assertBoxOk(
            """
            fun box(): String {
                var i = 0
                var sum = 0
                while (i < 10) {
                    i++
                    if (i % 2 == 0) continue
                    if (i > 7) break
                    sum += i
                }
                return if (sum == 16 && i == 9) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    @Disabled("TODO: do-while loop (repeat-until) with continue")
    fun `do-while loop executes at least once and supports continue`() {
        assertBoxOk(
            """
            fun box(): String {
                var once = 0
                do {
                    once++
                } while (false)

                var i = 0
                var oddSum = 0
                do {
                    i++
                    if (i % 2 == 0) continue
                    oddSum += i
                } while (i < 5)

                return if (once == 1 && oddSum == 9 && i == 5) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    @Disabled("TODO: Nested while loops with inner break and continue")
    fun `nested while loops keep inner break and continue scoped`() {
        assertBoxOk(
            """
            fun box(): String {
                var count = 0
                var i = 0
                while (i < 3) {
                    i++
                    var j = 0
                    while (j < 4) {
                        j++
                        if (j == 2) continue
                        if (j == 4) break
                        count++
                    }
                }
                return if (i == 3 && count == 6) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    @Disabled("TODO: Labeled break and continue across nested loops in Lua 5.1")
    fun `labeled break and continue on nested while loops`() {
        assertBoxOk(
            """
            fun box(): String {
                var total = 0
                var i = 0
                outer@ while (i < 4) {
                    i++
                    var j = 0
                    while (j < 4) {
                        j++
                        if (j == 2) continue@outer
                        if (i == 3 && j == 1) break@outer
                        total += i * 10 + j
                    }
                }
                return if (total == 32 && i == 3) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    @Disabled("TODO: ForLoopsLowering + IntRange / IntProgression in stdlib")
    fun `for loops over IntRange until downTo and step`() {
        assertBoxOk(
            """
            fun box(): String {
                var sum1 = 0
                for (i in 1..5) {
                    sum1 += i
                }

                var sum2 = 0
                for (i in 1 until 5) {
                    sum2 += i
                }

                var sum3 = 0
                for (i in 5 downTo 1 step 2) {
                    sum3 += i
                }

                return if (sum1 == 15 && sum2 == 10 && sum3 == 9) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }
}
