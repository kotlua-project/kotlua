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

class ArithmeticEdgeCasesBoxTest : AbstractKotluaTest() {
    @Test
    fun `compound assignments and prefix postfix increment decrement`() {
        assertBoxOk(
            """
            fun box(): String {
                var x = 10
                x += 5
                x -= 3
                x *= 2
                x /= 5
                x %= 3

                var a = 5
                val postInc = a++
                val preInc = ++a
                val postDec = a--
                val preDec = --a

                val ok =
                    x == 1 &&
                        postInc == 5 &&
                        preInc == 7 &&
                        postDec == 7 &&
                        preDec == 5 &&
                        a == 5
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `operator precedence and unary minus combinations`() {
        assertBoxOk(
            """
            fun box(): String {
                val a = 10 - 3 - 2
                val b = 10 - (3 - 2)
                val c = -2 + 3 * 4
                val d = -(2 + 3) * -2
                val e = +a
                val x = 5
                val f = x - -3
                val ok = a == 5 && b == 9 && c == 10 && d == 10 && e == 5 && f == 8
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `double arithmetic and mixed integral types`() {
        assertBoxOk(
            """
            fun box(): String {
                var d = 1.5
                d += 2.5
                d *= 3.0
                d /= 4.0
                val neg = -d
                val pre = ++d
                val post = d--

                val byteVal: Byte = 7
                val shortVal: Short = 3
                val mixed = (byteVal + shortVal) * 2 - 5

                val ok =
                    neg == -3.0 &&
                        pre == 4.0 &&
                        post == 4.0 &&
                        d == 3.0 &&
                        mixed == 15
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `non-finite double literals NaN and Infinity`() {
        assertBoxOk(
            """
            fun box(): String {
                val nan = 0.0 / 0.0
                val posInf = 1.0 / 0.0
                val negInf = -1.0 / 0.0
                val ok =
                    (nan != nan) &&
                        (posInf > 1e308) &&
                        (negInf < -1e308) &&
                        (posInf + negInf != posInf + negInf) &&
                        (posInf == 1.0 / 0.0) &&
                        (negInf == -1.0 / 0.0)
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }
}
