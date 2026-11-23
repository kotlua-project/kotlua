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

class PrimitiveRangeBoxTest : AbstractKotluaTest() {
    @Test
    fun `int rangeTo stored in variable and formatted`() =
        assertBoxOk(
            """
            fun makeRange(a: Int, b: Int): IntRange = a..b

            fun box(): String {
                val r = makeRange(2, 36)
                if (r.first != 2 || r.last != 36 || r.step != 1) return "FAIL: bounds"
                if (r.isEmpty()) return "FAIL: isEmpty"
                if (r.toString() != "2..36") return "FAIL: toString = ${'$'}r"
                if (10 !in r || 37 in r) return "FAIL: contains"

                val empty = makeRange(5, 2)
                if (!empty.isEmpty()) return "FAIL: empty"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `byte and short rangeTo outside loops`() =
        assertBoxOk(
            """
            fun box(): String {
                val b1: Byte = 1
                val b2: Byte = 4
                val s1: Short = 2
                val s2: Short = 5

                val bb = b1..b2
                if (bb.first != 1 || bb.last != 4) return "FAIL: Byte..Byte"

                val ss = s1..s2
                if (ss.first != 2 || ss.last != 5) return "FAIL: Short..Short"

                val bi = b1..3
                if (bi.first != 1 || bi.last != 3) return "FAIL: Byte..Int"

                val isRange = 1..s2
                if (isRange.first != 1 || isRange.last != 5) return "FAIL: Int..Short"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `char rangeTo outside loops`() =
        assertBoxOk(
            """
            fun makeCharRange(a: Char, b: Char): CharRange = a..b

            fun box(): String {
                val r = makeCharRange('a', 'd')
                if (r.first != 'a' || r.last != 'd') return "FAIL: bounds"
                if (r.isEmpty()) return "FAIL: isEmpty"
                if ('c' !in r || 'e' in r) return "FAIL: contains"
                if (r.toString() != "a..d") return "FAIL: toString = ${'$'}r"

                val empty = makeCharRange('z', 'a')
                if (!empty.isEmpty()) return "FAIL: empty"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `int and char rangeUntil outside loops`() =
        assertBoxOk(
            """
            fun box(): String {
                val r = 0..<4
                if (r.first != 0 || r.last != 3 || r.isEmpty()) return "FAIL: Int..<Int"

                val empty = 5..<5
                if (!empty.isEmpty()) return "FAIL: empty Int..<Int"

                val minEdge = 0..<Int.MIN_VALUE
                if (!minEdge.isEmpty()) return "FAIL: Int.MIN_VALUE edge"

                val cr = 'a'..<'d'
                if (cr.first != 'a' || cr.last != 'c' || cr.isEmpty()) return "FAIL: Char..<Char"

                val crMinEdge = 'a'..<'\u0000'
                if (!crMinEdge.isEmpty()) return "FAIL: Char zero edge"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `string and charSequence indexOf and lastIndexOf using rangeTo`() =
        assertBoxOk(
            """
            fun box(): String {
                val s = "ababa"
                if (s.indexOf("BA", ignoreCase = true) != 1) return "FAIL: string indexOf ignoreCase"
                if (s.lastIndexOf("BA", ignoreCase = true) != 3) return "FAIL: string lastIndexOf ignoreCase"

                val csq: CharSequence = StringBuilder("ababa")
                if (csq.indexOf("ba") != 1) return "FAIL: CharSequence indexOf"
                if (csq.lastIndexOf("ba") != 3) return "FAIL: CharSequence lastIndexOf"
                if (csq.indexOf("BA", ignoreCase = true) != 1) return "FAIL: CharSequence indexOf ignoreCase"
                if (csq.lastIndexOf("BA", ignoreCase = true) != 3) return "FAIL: CharSequence lastIndexOf ignoreCase"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `checkRadix error message formats 2 to 36 range`() =
        assertBoxOk(
            """
            fun box(): String {
                try {
                    10.toString(37)
                    return "FAIL: expected IllegalArgumentException"
                } catch (e: IllegalArgumentException) {
                    val msg = e.message ?: ""
                    if ("2..36" !in msg) return "FAIL: unexpected message: ${'$'}msg"
                }
                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `long rangeTo and rangeUntil outside loops`() =
        assertBoxOk(
            """
            fun box(): String {
                val a = 1L
                val b = 5L
                val r = a..b
                if (r.first != 1L || r.last != 5L || r.isEmpty()) return "FAIL: Long..Long"

                val u = 0L..<4L
                if (u.first != 0L || u.last != 3L || u.isEmpty()) return "FAIL: Long..<Long"

                val minEdge = 0L..<Long.MIN_VALUE
                if (!minEdge.isEmpty()) return "FAIL: Long.MIN_VALUE edge"

                return "OK"
            }
            """.trimIndent(),
        )
}
