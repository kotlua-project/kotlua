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

class FunctionReferencesBoxTest : AbstractKotluaTest() {
    @Test
    fun `basic lambdas with zero, single, multiple params, Unit return, and invoke`() {
        assertBoxOk(
            """
            fun box(): String {
                val zero: () -> Int = { 42 }
                val single: (Int) -> Int = { it * 2 }
                val add: (Int, Int) -> Int = { a, b -> a + b }
                var pinged = 0
                val ping: () -> Unit = { pinged++ }

                ping()
                val sum = zero() + single(7) + add(2, 3) + add.invoke(4, 6)
                return if (sum == 71 && pinged == 1) "OK" else "FAIL:" + sum + ":" + pinged
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `anonymous functions with local return and labeled lambda return`() {
        assertBoxOk(
            """
            fun box(): String {
                val anon = fun(x: Int, y: Int): Int {
                    if (x < 0) return -1
                    return x + y
                }
                val labeled: (Int) -> String = lbl@{ x ->
                    if (x == 0) return@lbl "zero"
                    "nz:" + x
                }

                val a = anon(-1, 5)
                val b = anon(3, 4)
                val s1 = labeled(0)
                val s2 = labeled(5)

                return if (a == -1 && b == 7 && s1 == "zero" && s2 == "nz:5") {
                    "OK"
                } else {
                    "FAIL:" + a + ":" + b + ":" + s1 + ":" + s2
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `higher-order function composition and returning lambdas`() {
        assertBoxOk(
            """
            private fun compose(f: (Int) -> Int, g: (Int) -> Int): (Int) -> Int = { x -> f(g(x)) }

            private fun makeAdder(base: Int): (Int) -> Int = { x -> base + x }

            fun box(): String {
                val h = compose({ it * 2 }, { it + 3 })
                val add10 = makeAdder(10)
                val r1 = h(4)
                val r2 = add10(5)
                return if (r1 == 14 && r2 == 15) "OK" else "FAIL:" + r1 + ":" + r2
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `returned lambdas created inside loop keep independent mutable boxes`() {
        assertBoxOk(
            """
            fun box(): String {
                var fn1: (() -> Int)? = null
                var fn2: (() -> Int)? = null

                var i = 0
                while (i < 2) {
                    i++
                    var iter = i * 10
                    val fn: () -> Int = {
                        iter += 1
                        iter
                    }
                    if (i == 1) fn1 = fn else fn2 = fn
                }

                val r1 = fn1!!()
                val r2 = fn1()
                val r3 = fn2!!()
                val sum = r1 + r2 + r3
                return if (r1 == 11 && r2 == 12 && r3 == 21 && sum == 44) {
                    "OK"
                } else {
                    "FAIL:" + r1 + ":" + r2 + ":" + r3
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `multi-level nested returned lambdas mutating shared and per-call vars`() {
        assertBoxOk(
            """
            fun box(): String {
                var a = 1
                val outerFn: () -> (() -> Int) = {
                    var b = 10
                    {
                        a += 2
                        b += 3
                        a * 100 + b
                    }
                }

                val f1 = outerFn()
                val v1 = f1()
                val v2 = f1()
                val f2 = outerFn()
                val v3 = f2()
                val v4 = f1()

                return if (v1 == 313 && v2 == 516 && v3 == 713 && v4 == 919 && a == 9) {
                    "OK"
                } else {
                    "FAIL:" + v1 + ":" + v2 + ":" + v3 + ":" + v4 + ":" + a
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `top-level function references including overloaded functions`() {
        assertBoxOk(
            """
            private fun inc(x: Int): Int = x + 1
            private fun calc(a: Int): Int = a * 2
            private fun calc(a: Int, b: Int): Int = a + b

            private fun applyOp(x: Int, op: (Int) -> Int): Int = op(x)

            fun box(): String {
                val ref1: (Int) -> Int = ::inc
                val ref2: (Int) -> Int = ::calc
                val ref3: (Int, Int) -> Int = ::calc

                val r1 = ref1(5)
                val r2 = ref2(4)
                val r3 = ref3(3, 7)
                val r4 = applyOp(10, ::inc)

                return if (r1 == 6 && r2 == 8 && r3 == 10 && r4 == 11) {
                    "OK"
                } else {
                    "FAIL:" + r1 + ":" + r2 + ":" + r3 + ":" + r4
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `bound local function reference with captured val and var`() {
        assertBoxOk(
            """
            fun box(): String {
                var acc = 10
                val scale = 2

                fun localAdd(x: Int): Int {
                    acc += x * scale
                    return acc
                }

                val fn: (Int) -> Int = ::localAdd
                val r1 = fn(3)
                acc += 4
                val r2 = fn(1)

                return if (r1 == 16 && r2 == 22 && acc == 22) {
                    "OK"
                } else {
                    "FAIL:" + r1 + ":" + r2 + ":" + acc
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `inline function with noinline, crossinline, and inline lambdas`() {
        assertBoxOk(
            """
            private inline fun mix(
                noinline first: (Int) -> Int,
                crossinline second: (Int) -> Int,
                third: (Int) -> Int,
            ): Int {
                val delayed: (Int) -> Int = { x -> second(x) + 1 }
                return first(10) + delayed(20) + third(30)
            }

            fun box(): String {
                var shared = 1
                val res =
                    mix(
                        first = { x ->
                            shared += 1
                            x + shared
                        },
                        second = { x ->
                            shared += 2
                            x * 2
                        },
                        third = { x ->
                            shared += 3
                            x + 5
                        },
                    )
                return if (res == 88 && shared == 7) "OK" else "FAIL:" + res + ":" + shared
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nullable function type with safe invoke and reassignment`() {
        assertBoxOk(
            """
            fun box(): String {
                var cb: ((Int) -> Int)? = null
                val before = cb?.invoke(5)
                cb = { it * 3 }
                val after = cb?.invoke(5)
                cb = null
                val cleared = cb?.invoke(5)

                return if (before == null && after == 15 && cleared == null) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `extension function type lambdas`() {
        assertBoxOk(
            """
            fun box(): String {
                val ext: String.(Int) -> String = { n -> this + ":" + n }
                val r1 = "hi".ext(3)
                val r2 = ext("yo", 4)
                return if (r1 == "hi:3" && r2 == "yo:4") "OK" else "FAIL:" + r1 + ":" + r2
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `adapted callable reference ignoring return value and immediately invoked lambda`() {
        assertBoxOk(
            """
            private var log = 0

            private fun sideEffect(x: Int): Int {
                log += x
                return x * 10
            }

            fun box(): String {
                log = 0
                val unitRef: (Int) -> Unit = ::sideEffect
                unitRef(5)
                unitRef(7)

                val direct = ({ a: Int, b: Int -> a + b })(3, 4)
                ({ log += 1 })()

                return if (log == 13 && direct == 7) "OK" else "FAIL:" + log + ":" + direct
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `bound and unbound member function references`() {
        assertBoxOk(
            """
            private class Boxed(val base: Int) {
                fun add(x: Int): Int = base + x
            }

            fun box(): String {
                val b = Boxed(10)
                val bound: (Int) -> Int = b::add
                val unbound: (Boxed, Int) -> Int = Boxed::add
                return if (bound(5) == 15 && unbound(b, 7) == 17) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    @Disabled("TODO: OOP / SAM conversions")
    fun `SAM conversion for fun interface`() {
        assertBoxOk(
            """
            private fun interface IntOp {
                fun apply(x: Int): Int
            }

            private fun runOp(x: Int, op: IntOp): Int = op.apply(x)

            fun box(): String {
                val res = runOp(6) { it * it }
                return if (res == 36) "OK" else "FAIL:" + res
            }
            """.trimIndent(),
        )
    }
}
