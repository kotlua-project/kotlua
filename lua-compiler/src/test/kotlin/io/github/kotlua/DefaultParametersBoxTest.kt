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

class DefaultParametersBoxTest : AbstractKotluaTest() {
    @Test
    fun `basic defaults, named arguments, laziness, and evaluation order`() {
        assertBoxOk(
            """
            private var log = ""

            private fun record(
                tag: String,
                value: Int,
            ): Int {
                log += tag + ","
                return value
            }

            private fun combine(
                a: Int = record("da", 1),
                b: Int = record("db", 2),
                c: Int = record("dc", 3),
            ): Int = a * 100 + b * 10 + c

            fun box(): String {
                log = ""
                val r1 = combine()
                log += "|"
                val r2 = combine(4)
                log += "|"
                val r3 = combine(c = record("ec", 9), a = record("ea", 7))
                log += "|"
                val r4 = combine(record("ea2", 5), record("eb2", 6), record("ec2", 8))

                val expectedLog = "da,db,dc,|db,dc,|ec,ea,db,|ea2,eb2,ec2,"
                return if (r1 == 123 && r2 == 423 && r3 == 729 && r4 == 568 && log == expectedLog) {
                    "OK"
                } else {
                    "FAIL:" + r1 + ":" + r2 + ":" + r3 + ":" + r4 + ":" + log
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nullable defaults preserve explicit null and falsy values`() {
        assertBoxOk(
            """
            private fun fmt(
                s: String? = "def",
                b: Boolean? = true,
                n: Int? = 42,
            ): String {
                val sPart = s ?: "NULL"
                val bPart = if (b == null) "NULL" else b.toString()
                val nPart = n?.toString() ?: "NULL"
                return sPart + ":" + bPart + ":" + nPart
            }

            fun box(): String {
                val r1 = fmt()
                val r2 = fmt(null, null, null)
                val r3 = fmt("", false, 0)
                val r4 = fmt(b = false)
                val r5 = fmt(n = null)

                return if (r1 == "def:true:42" &&
                    r2 == "NULL:NULL:NULL" &&
                    r3 == ":false:0" &&
                    r4 == "def:false:42" &&
                    r5 == "def:true:NULL"
                ) {
                    "OK"
                } else {
                    "FAIL:" + r1 + "|" + r2 + "|" + r3 + "|" + r4 + "|" + r5
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `default expressions referencing earlier parameters and extension receiver`() {
        assertBoxOk(
            """
            private fun seq(
                a: Int,
                b: Int = a * 2,
                c: Int = a + b,
            ): Int = a * 100 + b * 10 + c

            private fun Int.scale(
                factor: Int = this,
                offset: Int = this + factor,
            ): Int = this * factor + offset

            fun box(): String {
                val s1 = seq(2)
                val s2 = seq(2, 5)
                val s3 = seq(2, c = 9)

                val e1 = 3.scale()
                val e2 = 3.scale(4)
                val e3 = 3.scale(offset = 1)

                return if (s1 == 246 && s2 == 257 && s3 == 249 &&
                    e1 == 15 && e2 == 19 && e3 == 10
                ) {
                    "OK"
                } else {
                    "FAIL:" + s1 + ":" + s2 + ":" + s3 + ":" + e1 + ":" + e2 + ":" + e3
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `local functions and closures with default parameters`() {
        assertBoxOk(
            """
            fun box(): String {
                var calls = 0
                fun step(
                    inc: Int = 5,
                    mul: Int = inc + 1,
                ): Int {
                    calls++
                    return inc * mul
                }

                val a = step()
                val b = step(3)
                val c = step(mul = 10)

                var base = 10
                fun addFromBase(delta: Int = base * 2): Int {
                    base += 1
                    return delta
                }

                val r1 = addFromBase()
                val r2 = addFromBase()
                val r3 = addFromBase(7)

                return if (a == 30 && b == 12 && c == 50 && calls == 3 &&
                    r1 == 20 && r2 == 22 && r3 == 7 && base == 13
                ) {
                    "OK"
                } else {
                    "FAIL"
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `default lambda arguments in regular and inline functions`() {
        assertBoxOk(
            """
            private fun applyWithDefault(
                x: Int,
                factor: Int = 3,
                fn: (Int, Int) -> Int = { a, b -> a * b },
            ): Int = fn(x, factor)

            private inline fun runOp(
                a: Int,
                b: Int = a + 2,
                op: (Int, Int) -> Int = { x, y -> x * 10 + y },
            ): Int = op(a, b)

            fun box(): String {
                val r1 = applyWithDefault(4)
                val r2 = applyWithDefault(4, 5)
                val r3 = applyWithDefault(4, fn = { a, b -> a + b })

                val i1 = runOp(3)
                val i2 = runOp(3, 7)
                val i3 = runOp(3) { x, y -> x + y }

                return if (r1 == 12 && r2 == 20 && r3 == 7 &&
                    i1 == 35 && i2 == 37 && i3 == 8
                ) {
                    "OK"
                } else {
                    "FAIL:" + r1 + ":" + r2 + ":" + r3 + ":" + i1 + ":" + i2 + ":" + i3
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `recursion, loops, overloads, and conditional defaults`() {
        assertBoxOk(
            """
            private fun sumTo(
                n: Int,
                acc: Int = 0,
            ): Int = if (n <= 0) acc else sumTo(n - 1, acc + n)

            private fun overloaded(
                x: Int,
                y: Int = 10,
            ): Int = x + y

            private fun overloaded(
                x: String,
                y: Int = 20,
            ): String = x + ":" + y

            fun box(): String {
                val rec = sumTo(4)
                val o1 = overloaded(5)
                val o2 = overloaded("k")
                val o3 = overloaded("k", 99)

                var tick = 0
                fun nextTick(): Int {
                    tick++
                    return tick * 10
                }
                fun stepVal(
                    flag: Boolean,
                    v: Int = if (flag) nextTick() else -1,
                ): Int = v

                var loopSum = 0
                var i = 0
                while (i < 4) {
                    i++
                    loopSum +=
                        when (i) {
                            2 -> stepVal(false)
                            3 -> stepVal(true, 5)
                            else -> stepVal(true)
                        }
                }

                return if (rec == 10 && o1 == 15 && o2 == "k:20" && o3 == "k:99" &&
                    loopSum == 34 && tick == 2
                ) {
                    "OK"
                } else {
                    "FAIL:" + rec + ":" + o1 + ":" + o2 + ":" + o3 + ":" + loopSum + ":" + tick
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `more than 32 default parameters using multiple bitmasks`() {
        assertBoxOk(
            """
            private fun manyDefaults(
                p0: Int = 1, p1: Int = 1, p2: Int = 1, p3: Int = 1, p4: Int = 1,
                p5: Int = 1, p6: Int = 1, p7: Int = 1, p8: Int = 1, p9: Int = 1,
                p10: Int = 1, p11: Int = 1, p12: Int = 1, p13: Int = 1, p14: Int = 1,
                p15: Int = 1, p16: Int = 1, p17: Int = 1, p18: Int = 1, p19: Int = 1,
                p20: Int = 1, p21: Int = 1, p22: Int = 1, p23: Int = 1, p24: Int = 1,
                p25: Int = 1, p26: Int = 1, p27: Int = 1, p28: Int = 1, p29: Int = 1,
                p30: Int = 1, p31: Int = 1, p32: Int = 1, p33: Int = 1, p34: Int = 1,
            ): Int =
                p0 + p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 +
                    p10 + p11 + p12 + p13 + p14 + p15 + p16 + p17 + p18 + p19 +
                    p20 + p21 + p22 + p23 + p24 + p25 + p26 + p27 + p28 + p29 +
                    p30 + p31 + p32 + p33 + p34

            fun box(): String {
                val allDefaults = manyDefaults()
                val acrossMaskBoundary = manyDefaults(p0 = 10, p31 = 20, p32 = 30, p34 = 40)
                return if (allDefaults == 35 && acrossMaskBoundary == 131) {
                    "OK"
                } else {
                    "FAIL:" + allDefaults + ":" + acrossMaskBoundary
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `multi-level nested local functions with defaults and captured mutable vars`() {
        assertBoxOk(
            """
            fun box(): String {
                var log = ""
                var outerMul = 2

                fun level1(
                    a: Int = 3,
                    b: Int = a * outerMul,
                ): Int {
                    var midOffset = 10

                    fun level2(
                        c: Int = b + 1,
                        d: Int = c + midOffset,
                    ): Int {
                        fun level3(
                            e: Int = d * 2,
                            tag: String = "L3",
                        ): Int {
                            log += tag + "(" + a + "," + b + "," + c + "," + d + "," + e + ");"
                            midOffset += 5
                            outerMul += 1
                            return e + midOffset
                        }

                        return if (c > 10) level3(tag = "BIG") else level3()
                    }

                    val r1 = level2()
                    val r2 = level2(c = 12)
                    return r1 + r2 + midOffset
                }

                val res1 = level1()
                val res2 = level1(a = 4)

                val expectedLog =
                    "L3(3,6,7,17,34);BIG(3,6,12,27,54);" +
                        "BIG(4,16,17,27,54);BIG(4,16,12,27,54);"

                return if (res1 == 143 && res2 == 163 && outerMul == 6 && log == expectedLog) {
                    "OK"
                } else {
                    "FAIL:" + res1 + ":" + res2 + ":" + outerMul + ":" + log
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested recursive function with defaults, sibling name collisions, and inline block`() {
        assertBoxOk(
            """
            private inline fun runWith(
                seed: Int,
                block: (Int) -> Int,
            ): Int = block(seed)

            fun box(): String {
                var calls = 0

                fun sumDown(
                    n: Int,
                    step: Int = 1,
                    acc: Int = n,
                ): Int {
                    calls++
                    if (n <= 0) return acc
                    return sumDown(n - step, acc = acc + n)
                }

                fun launch(start: Int = 4): Int = sumDown(start)

                val r1 = launch()
                val r2 = sumDown(6, step = 2)

                var siblingAcc = 0
                if (calls > 0) {
                    fun calc(
                        x: Int,
                        y: Int = 10,
                    ): Int = x + y
                    siblingAcc += calc(5)
                    siblingAcc += calc(5, 1)
                }
                if (calls > 0) {
                    fun calc(
                        x: Int,
                        y: Int = 100,
                    ): Int = x * y
                    siblingAcc += calc(2)
                    siblingAcc += calc(2, 3)
                }

                val inlineRes =
                    runWith(7) { v ->
                        fun inner(
                            a: Int = v,
                            b: Int = a * 2,
                        ): Int = a + b
                        inner() + inner(b = 1)
                    }

                return if (r1 == 14 && r2 == 22 && calls == 11 && siblingAcc == 227 && inlineRes == 29) {
                    "OK"
                } else {
                    "FAIL:" + r1 + ":" + r2 + ":" + calls + ":" + siblingAcc + ":" + inlineRes
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nested function with defaults called from escaping lambda and inside loop`() {
        assertBoxOk(
            """
            private fun makeWorker(base: Int): (Int) -> Int {
                var hits = 0

                fun compute(
                    x: Int,
                    mul: Int = base,
                    add: Int = mul + hits,
                ): Int {
                    hits++
                    return x * mul + add
                }

                return { v ->
                    if (v < 0) compute(-v) else compute(v, add = 10)
                }
            }

            fun box(): String {
                val worker = makeWorker(3)
                val workerSum = worker(-2) + worker(-2) + worker(5) + worker(-1)

                var loopTotal = 0
                var bonus = 1
                var i = 0
                while (i < 3) {
                    i++
                    val iterBase = i * 10
                    fun iterFn(
                        x: Int = iterBase,
                        mapper: (Int) -> Int = { it + bonus },
                    ): Int {
                        bonus++
                        return mapper(x)
                    }
                    loopTotal += iterFn()
                    if (i == 2) {
                        loopTotal += iterFn(x = 100)
                    }
                }

                return if (workerSum == 53 && loopTotal == 174 && bonus == 5) {
                    "OK"
                } else {
                    "FAIL:" + workerSum + ":" + loopTotal + ":" + bonus
                }
            }
            """.trimIndent(),
        )
    }
}
