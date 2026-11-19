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

class NullableBoxTest : AbstractKotluaTest() {
    @Test
    fun `safe calls, elvis chains, and not-null assertion evaluate receivers once`() {
        assertBoxOk(
            """
            private var calls = 0

            private fun maybeText(n: Int): String? {
                calls++
                return if (n % 2 == 0) "s" + n else null
            }

            fun box(): String {
                calls = 0
                val a = maybeText(2)!!.length
                val b = maybeText(1)?.length ?: -1
                val c = maybeText(4)?.length ?: -1
                val d = (maybeText(1) ?: maybeText(3) ?: maybeText(6))!!

                return if (calls == 6 && a == 2 && b == -1 && c == 2 && d == "s6") {
                    "OK"
                } else {
                    "FAIL:" + calls + ":" + a + ":" + b + ":" + c + ":" + d
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `falsy Boolean, zero Int, and empty String are distinct from null`() {
        assertBoxOk(
            """
            private fun nullableBool(v: Boolean?): Boolean? = v

            private fun nullableInt(v: Int?): Int? = v

            private fun nullableStr(v: String?): String? = v

            fun box(): String {
                val f = nullableBool(false)
                val nBool = nullableBool(null)

                val z = nullableInt(0)
                val nInt = nullableInt(null)

                val empty = nullableStr("")
                val nStr = nullableStr(null)

                val okBool = (f ?: true) == false && (nBool ?: true) == true && f == false && f != nBool && nBool == null
                val okInt = (z ?: 99) == 0 && (nInt ?: 99) == 99 && z == 0 && z != nInt && nInt == null
                val okStr = (empty ?: "def") == "" && (nStr ?: "def") == "def" && empty != nStr && empty!!.length == 0

                return if (okBool && okInt && okStr) {
                    "OK"
                } else {
                    "FAIL:" + okBool + ":" + okInt + ":" + okStr
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nullable values in while, do-while and labeled loops with elvis jumps`() {
        assertBoxOk(
            """
            private fun nextItem(step: Int): Int? =
                when (step) {
                    2 -> null
                    4 -> 0
                    5 -> null
                    6 -> 10
                    else -> step
                }

            fun box(): String {
                var sum = 0
                var trace = ""
                var i = 0
                outer@ while (i < 3) {
                    i++
                    var j = 0
                    while (j < 6) {
                        j++
                        val raw: Int? = if (i == 2 && j == 3) null else nextItem(j)
                        if (i == 2 && j == 3) {
                            raw ?: continue@outer
                        }
                        if (i == 3 && j == 2) {
                            val stop = raw ?: break@outer
                            sum += stop
                        }
                        val v = raw ?: continue
                        val nonNull = if (v == 0) (nextItem(j)!! + 100) else v
                        sum += nonNull
                        trace += i.toString() + "." + j + "=" + nonNull + ","
                    }
                }

                var cur: Int? = 1
                var doSum = 0
                do {
                    val value = cur!!
                    doSum += value
                    cur = if (value < 3) value + 1 else null
                } while (cur != null)

                val expectedTrace = "1.1=1,1.3=3,1.4=100,1.6=10,2.1=1,3.1=1,"
                return if (sum == 116 && trace == expectedTrace && doSum == 6 && cur == null) {
                    "OK"
                } else {
                    "FAIL:" + sum + ":" + doSum + ":" + trace
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nullable types and !! with inline functions and non-local loop jumps`() {
        assertBoxOk(
            """
            private inline fun <T : Any, R> T?.ifPresent(block: (T) -> R): R? =
                if (this != null) block(this) else null

            private inline fun <T> withRetryNullable(
                attempts: Int,
                producer: (Int) -> T?,
            ): T? {
                var i = 0
                while (i < attempts) {
                    i++
                    val candidate = producer(i)
                    if (candidate != null) return candidate
                }
                return null
            }

            fun box(): String {
                var label: String? = null
                val r1 = label.ifPresent { it.length } ?: -5
                label = "kotlua"
                val r2 = label.ifPresent { it.length }!!

                val found: Int? = withRetryNullable(4) { step -> if (step == 3) 77 else null }
                val missed: Int? = withRetryNullable(2) { null }
                val combined = found!! + (missed ?: 23)

                var acc = 0
                var k = 0
                while (k < 5) {
                    k++
                    val v: Int? =
                        when (k) {
                            2, 4 -> null
                            else -> k * 10
                        }
                    if (v == null) {
                        if (k == 4) break else continue
                    }
                    val added = v.ifPresent { it }!!
                    acc += added
                }

                return if (r1 == -5 && r2 == 6 && combined == 100 && missed == null && acc == 40) {
                    "OK"
                } else {
                    "FAIL:" + r1 + ":" + r2 + ":" + combined + ":" + acc
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `smart casts, when on nullable values, and top-level mutable nullable var`() {
        assertBoxOk(
            """
            private var globalNullable: String? = null

            private fun classify(x: Int?): String =
                when (x) {
                    null -> "nil"
                    0 -> "zero"
                    else -> "num:" + x
                }

            private fun combine(
                a: String?,
                b: String?,
            ): String {
                if (a != null && b != null) {
                    return a + ":" + b
                }
                return (a ?: "_") + "|" + (b ?: "_")
            }

            fun box(): String {
                globalNullable = null
                val wasNull = globalNullable == null
                globalNullable = "ok"
                val len = globalNullable!!.length
                globalNullable = null
                val cleared = globalNullable == null

                val c1 = classify(null)
                val c2 = classify(0)
                val c3 = classify(5)

                val p1 = combine("x", "y")
                val p2 = combine(null, "y")
                val p3 = combine("x", null)

                return if (wasNull && len == 2 && cleared &&
                    c1 == "nil" && c2 == "zero" && c3 == "num:5" &&
                    p1 == "x:y" && p2 == "_|y" && p3 == "x|_"
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
    fun `nullable captured vars in local functions and non-inline lambdas`() {
        assertBoxOk(
            """
            private fun makeTracker(): (Int?) -> Int? {
                var last: Int? = null
                return { next ->
                    val prev = last
                    last = next
                    prev
                }
            }

            fun box(): String {
                var state: String? = null
                fun push(v: String?) {
                    state = v
                }
                fun popOrDefault(def: String): String {
                    val cur = state
                    state = null
                    return cur ?: def
                }

                val transform: (String?) -> Int? = { s ->
                    s?.length?.let { it + 1 }
                }

                val emptyPop = popOrDefault("empty")
                push("abc")
                val t1 = transform(state)!!
                val popped = popOrDefault("empty")
                val t2 = transform(state)

                val tracker = makeTracker()
                val k1 = tracker(10)
                val k2 = tracker(null)
                val k3 = tracker(25)
                val k4 = tracker(0)!!
                val k5 = tracker(null)!!
                val k6 = tracker(1)

                val okLocal = emptyPop == "empty" && t1 == 4 && popped == "abc" && t2 == null
                val okLambda = k1 == null && k2 == 10 && k3 == null && k4 == 25 && k5 == 0 && k6 == null

                return if (okLocal && okLambda) "OK" else "FAIL"
            }

            private inline fun <T, R> T.let(block: (T) -> R): R = block(this)
            """.trimIndent(),
        )
    }
}
