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

class InlineFunctionsBoxTest : AbstractKotluaTest() {
    @Test
    fun `custom inline functions with and without lambdas`() {
        assertBoxOk(
            """
            inline fun doubleIt(x: Int): Int = x * 2

            inline fun compute(a: Int, b: Int, op: (Int, Int) -> Int): Int = op(a, b)

            inline fun applyTwice(x: Int, fn: (Int) -> Int): Int = fn(fn(x))

            fun box(): String {
                val res = applyTwice(doubleIt(3)) { it + 1 } + compute(10, 20) { x, y -> x + y }
                return if (res == 38) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `stdlib scope functions let also apply`() {
        assertBoxOk(
            """
            fun box(): String {
                var side = 0
                val a = "O".let { it + "K" }
                val b = 10.also { side = side + it }.let { it * 2 }
                val c = 12.apply { side = side + this }
                val ok = a == "OK" && b == 20 && c == 12 && side == 22
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `labeled return from inline lambda`() {
        assertBoxOk(
            """
            inline fun transform(x: Int, block: (Int) -> String): String = block(x)

            fun classify(n: Int): String =
                transform(n) { v ->
                    if (v < 0) return@transform "NEG"
                    if (v == 0) return@transform "ZERO"
                    "POS"
                }

            fun box(): String {
                val s1 = classify(-1)
                val s2 = classify(0)
                val s3 = classify(5)
                val s4 =
                    42.let {
                        if (it > 10) return@let "OK"
                        "FAIL"
                    }
                val ok = s1 == "NEG" && s2 == "ZERO" && s3 == "POS" && s4 == "OK"
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `non-local return from inline lambda exits enclosing function`() {
        assertBoxOk(
            """
            inline fun execute(block: () -> Unit) {
                block()
            }

            fun findFirstPositive(a: Int, b: Int): Int {
                execute {
                    if (a > 0) return a
                }
                execute {
                    if (b > 0) return b
                }
                return -1
            }

            fun box(): String {
                val ok =
                    findFirstPositive(-5, 42) == 42 &&
                        findFirstPositive(7, 99) == 7 &&
                        findFirstPositive(-1, -2) == -1
                "dummy".let {
                    return if (ok) "OK" else "FAIL"
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested inline lambdas with non-local return`() {
        assertBoxOk(
            """
            inline fun outer(block: () -> Unit) {
                block()
            }

            inline fun inner(block: () -> Unit) {
                block()
            }

            fun earlyExit(flag: Boolean): String {
                outer {
                    inner {
                        if (flag) return "OK"
                    }
                }
                return "FAIL"
            }

            fun box(): String {
                val ok = earlyExit(true) == "OK" && earlyExit(false) == "FAIL"
                if (ok) {
                    1.also {
                        2.let {
                            return "OK"
                        }
                    }
                }
                return "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `non-local break and continue inside inline lambdas`() {
        assertBoxOk(
            """
            inline fun runStep(block: () -> Unit) {
                block()
            }

            fun box(): String {
                var i = 0
                var sum = 0
                while (i < 10) {
                    i++
                    runStep {
                        if (i % 2 == 0) continue
                        if (i > 7) break
                        sum += i
                    }
                }

                var j = 0
                while (j < 5) {
                    j++
                    j.let {
                        if (it == 3) break
                    }
                }

                return if (sum == 16 && i == 9 && j == 3) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }
}
