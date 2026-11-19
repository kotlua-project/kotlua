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

class ClosuresBoxTest : AbstractKotluaTest() {
    @Test
    fun `capture val and var with bidirectional updates`() {
        assertBoxOk(
            """
            fun box(): String {
                val multiplier = 3
                val prefix = "res="
                var acc = 10

                fun addScaled(x: Int) {
                    acc += x * multiplier
                }

                addScaled(2)
                acc += 4
                addScaled(1)

                val out = prefix + acc
                return if (acc == 23 && out == "res=23") "OK" else "FAIL:" + out
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `uninitialized captured var initialized conditionally`() {
        assertBoxOk(
            """
            private fun pick(flag: Boolean): String {
                var res: String
                if (flag) {
                    res = "A"
                } else {
                    res = "B"
                }
                fun appendAndGet(suffix: String): String {
                    res += suffix
                    return res
                }
                return appendAndGet("!")
            }

            fun box(): String {
                val combined = pick(true) + pick(false)
                return if (combined == "A!B!") "OK" else "FAIL:" + combined
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nullable captured var set to null and reassigned`() {
        assertBoxOk(
            """
            fun box(): String {
                var value: Int? = null

                fun read(): Int? = value
                fun write(v: Int?) {
                    value = v
                }

                val r1 = read()
                write(42)
                val r2 = read()
                write(null)
                val r3 = read()
                value = 7
                val r4 = read()

                return if (r1 == null && r2 == 42 && r3 == null && r4 == 7) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `multi-level nested local functions mutating outer vars`() {
        assertBoxOk(
            """
            fun box(): String {
                var a = 1

                fun l1(x: Int): Int {
                    var b = 10
                    fun l2(y: Int): Int {
                        var c = 100
                        fun l3(z: Int) {
                            a += z
                            b += z * 2
                            c += z * 3
                        }
                        l3(y)
                        return c
                    }
                    val cRes = l2(x)
                    return b + cRes
                }

                val r1 = l1(2)
                val r2 = l1(3)
                return if (a == 6 && r1 == 120 && r2 == 125) {
                    "OK"
                } else {
                    "FAIL:" + a + ":" + r1 + ":" + r2
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `multiple local functions sharing same captured vars`() {
        assertBoxOk(
            """
            fun box(): String {
                var count = 0
                var log = ""

                fun inc(d: Int) {
                    count += d
                    log += "+" + d + ","
                }

                fun dec(d: Int) {
                    count -= d
                    log += "-" + d + ","
                }

                fun snapshot(): String = count.toString() + ":" + log

                inc(5)
                dec(2)
                inc(4)

                val snap = snapshot()
                return if (snap == "7:+5,-2,+4,") "OK" else "FAIL:" + snap
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `recursive and chained local functions capturing vars`() {
        assertBoxOk(
            """
            fun box(): String {
                var steps = 0
                var trace = ""

                fun rec(n: Int): Int {
                    steps++
                    trace += n.toString() + ","
                    if (n <= 1) return 1
                    return n + rec(n - 1)
                }

                val sum = rec(4)

                var acc = 0
                fun innerA(v: Int) {
                    acc += v
                }
                fun innerB(v: Int) {
                    innerA(v * 2)
                    acc += 1
                }
                innerB(5)
                innerB(3)

                return if (sum == 10 && steps == 4 && trace == "4,3,2,1," && acc == 18) {
                    "OK"
                } else {
                    "FAIL:" + sum + ":" + steps + ":" + trace + ":" + acc
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `captured var declared inside loop body re-initializes per iteration`() {
        assertBoxOk(
            """
            fun box(): String {
                var total = 0
                var i = 0
                while (i < 4) {
                    i++
                    var localAcc = i * 10
                    fun bump(delta: Int) {
                        localAcc += delta
                        total += localAcc
                    }
                    bump(i)
                    bump(1)
                }
                return if (total == 224) "OK" else "FAIL:" + total
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `captured var mutated by local function in do-while condition with labeled continue`() {
        assertBoxOk(
            """
            fun box(): String {
                var calls = 0
                fun checkCond(j: Int): Boolean {
                    calls++
                    return j < 3
                }

                var sum = 0
                var i = 0
                outer@ while (i < 3) {
                    i++
                    var j = 0
                    do {
                        j++
                        if (i == 2 && j == 1) continue@outer
                        if (j == 2) continue
                        sum += i * 10 + j
                    } while (checkCond(j))
                }

                return if (sum == 88 && calls == 6) "OK" else "FAIL:" + sum + ":" + calls
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `captured parameters, variable shadowing, and sibling local functions with same name`() {
        assertBoxOk(
            """
            private fun compute(base: Int, step: Int): Int {
                var acc = base
                if (step > 0) {
                    fun apply(v: Int) {
                        acc += v + step
                    }
                    apply(1)
                    apply(2)
                }
                if (step > 0) {
                    fun apply(v: Int) {
                        acc += v * 10
                    }
                    apply(3)
                }
                if (true) {
                    val acc = 999
                    if (acc != 999) return -1
                }
                return acc
            }

            private fun other(x: Int): Int {
                var acc = x
                fun apply(v: Int) {
                    acc *= v
                }
                apply(3)
                return acc
            }

            fun box(): String {
                val r1 = compute(5, 2)
                val r2 = other(4)
                return if (r1 == 42 && r2 == 12) "OK" else "FAIL:" + r1 + ":" + r2
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `prefix postfix increment and compound assignment on captured var`() {
        assertBoxOk(
            """
            fun box(): String {
                var x = 10
                fun bumpAndGetOld(): Int = x++
                fun bumpAndGetNew(): Int = ++x

                val a = bumpAndGetOld()
                val b = bumpAndGetNew()
                val c = x++
                val d = ++x
                if (a == 10) x += 6 else x = 0

                return if (a == 10 && b == 12 && c == 12 && d == 14 && x == 20) {
                    "OK"
                } else {
                    "FAIL:" + a + ":" + b + ":" + c + ":" + d + ":" + x
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `captured var shared between inline lambda and local function`() {
        assertBoxOk(
            """
            private inline fun repeatTimes(n: Int, action: (Int) -> Unit) {
                var k = 0
                while (k < n) {
                    k++
                    action(k)
                }
            }

            fun box(): String {
                var sum = 0
                fun addDouble(v: Int) {
                    sum += v * 2
                }

                repeatTimes(3) { k ->
                    sum += k
                    addDouble(k)
                }

                fun getSum(): Int = sum
                val res = getSum()
                return if (res == 18) "OK" else "FAIL:" + res
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `returning closure factory with independent counters`() {
        assertBoxOk(
            """
            private fun makeCounter(start: Int): () -> Int {
                var current = start
                return {
                    current++
                    current
                }
            }

            fun box(): String {
                val c1 = makeCounter(0)
                val c2 = makeCounter(10)
                val sum = c1() + c1() + c2() + c1() + c2()
                return if (sum == 1 + 2 + 11 + 3 + 12) "OK" else "FAIL:" + sum
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `non-inline lambda passed to higher-order function mutating outer var`() {
        assertBoxOk(
            """
            private fun applyTwice(x: Int, fn: (Int) -> Unit) {
                fn(x)
                fn(x * 2)
            }

            fun box(): String {
                var total = 0
                applyTwice(5) { v -> total += v }
                return if (total == 15) "OK" else "FAIL:" + total
            }
            """.trimIndent(),
        )
    }
}
