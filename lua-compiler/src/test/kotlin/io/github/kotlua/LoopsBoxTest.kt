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

    @Test
    fun `three-level nested while and do-while with multiple labeled continues and breaks`() {
        assertBoxOk(
            """
            fun box(): String {
                var midCondCalls = 0

                fun checkMid(j: Int): Boolean {
                    midCondCalls++
                    return j < 3
                }
                var trace = ""
                var i = 0
                outer@ while (i < 4) {
                    i++
                    trace += "O" + i + "("
                    var j = 0
                    mid@ do {
                        j++
                        trace += "M" + j + "["
                        var k = 0
                        inner@ while (k < 4) {
                            k++
                            if (k == 2) {
                                trace += "ci,"
                                continue@inner
                            }
                            if (i == 1 && j == 1 && k == 3) {
                                trace += "bi,"
                                break@inner
                            }
                            if (i == 1 && j == 2 && k == 1) {
                                trace += "cm,"
                                continue@mid
                            }
                            if (i == 3 && j == 1 && k == 3) {
                                trace += "bm,"
                                break@mid
                            }
                            if (i == 2 && j == 1 && k == 3) {
                                trace += "co,"
                                continue@outer
                            }
                            if (i == 4 && j == 2 && k == 1) {
                                trace += "bo,"
                                break@outer
                            }
                            trace += k.toString() + ","
                        }
                        trace += "]afterI,"
                    } while (checkMid(j))
                    trace += ")afterM;"
                }

                var postSum = 0
                var p = 0
                while (p < 5) {
                    p++
                    if (p == 2 || p == 4) continue
                    if (p == 5) break
                    postSum += p
                }

                val expectedTrace =
                    "O1(M1[1,ci,bi,]afterI,M2[cm,M3[1,ci,3,4,]afterI,)afterM;" +
                        "O2(M1[1,ci,co," +
                        "O3(M1[1,ci,bm,)afterM;" +
                        "O4(M1[1,ci,3,4,]afterI,M2[bo,"

                return if (midCondCalls == 4 && postSum == 4 && p == 5 && trace == expectedTrace) {
                    "OK"
                } else {
                    "FAIL:" + midCondCalls + ":" + postSum + ":" + p + ":" + trace
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `composite loop conditions with labeled break and continue`() {
        assertBoxOk(
            """
            private inline fun <T> eval(block: () -> T): T = block()

            fun box(): String {
                var condLogs = ""
                var outerCount = 0
                var sum = 0

                outer@ while (eval {
                        condLogs += "W;"
                        outerCount < 3
                    }
                ) {
                    outerCount++
                    var innerCount = 0
                    do {
                        innerCount++
                        if (innerCount == 1 && outerCount == 2) {
                            condLogs += "co;"
                            continue@outer
                        }
                        if (innerCount == 2) {
                            condLogs += "ci;"
                            continue
                        }
                        if (innerCount == 3 && outerCount == 3) {
                            condLogs += "bo;"
                            break@outer
                        }
                        if (innerCount == 3) {
                            condLogs += "bi;"
                            break
                        }
                        sum += outerCount * 10 + innerCount
                    } while (eval {
                        condLogs += "D" + outerCount + "." + innerCount + ";"
                        innerCount < 4
                    })
                }

                val expectedLogs = "W;D1.1;ci;D1.2;bi;W;co;W;D3.1;ci;D3.2;bo;"
                return if (sum == 42 && condLogs == expectedLogs) "OK" else "FAIL:" + sum + ":" + condLogs
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `expression-position jumps, dead code after jump, and local function with same label`() {
        assertBoxOk(
            """
            fun box(): String {
                fun helper(limit: Int): Int {
                    var acc = 0
                    var a = 0
                    outer@ while (a < limit) {
                        a++
                        var b = 0
                        while (b < 4) {
                            b++
                            val v =
                                when {
                                    b == 2 -> continue
                                    a == 2 && b == 3 -> continue@outer
                                    a == 3 && b == 3 -> break@outer
                                    else -> a * 10 + b
                                }
                            acc += v
                        }
                    }
                    return acc
                }

                var total = 0
                var x = 0
                outer@ while (x < 3) {
                    x++
                    var y = 0
                    while (y < 3) {
                        y++
                        val delta =
                            if (y == 2) {
                                continue
                            } else if (x == 3 && y == 3) {
                                break@outer
                            } else {
                                helper(x)
                            }
                        total += delta
                        if (x == 2 && y == 1) {
                            continue@outer
                            @Suppress("UNREACHABLE_CODE")
                            total += 9999
                        }
                    }
                }

                return if (total == 225) "OK" else "FAIL:" + total
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `non-local break, continue and return across inline blocks inside nested loops`() {
        assertBoxOk(
            """
            private inline fun <T> myRun(block: () -> T): T = block()

            fun box(): String {
                var trace = ""
                var i = 0
                outer@ while (i < 4) {
                    i++
                    val step =
                        myRun {
                            if (i == 2) continue@outer
                            if (i == 4) break@outer
                            var k = 0
                            while (k < 3) {
                                k++
                                if (k == 2 && i == 3) return@myRun "early3"
                            }
                            "norm" + i
                        }
                    trace += step + ";"
                }
                return if (trace == "norm1;early3;") "OK" else "FAIL:" + trace
            }
            """.trimIndent(),
        )
    }
}
