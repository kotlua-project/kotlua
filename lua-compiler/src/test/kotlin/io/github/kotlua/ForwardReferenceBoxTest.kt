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

class ForwardReferenceBoxTest : AbstractKotluaTest() {
    @Test
    fun `top-level function calls function declared later`() {
        assertBoxOk(
            """
            fun box(): String {
                return if (first() == 42) "OK" else "FAIL"
            }

            fun first(): Int = second()

            fun second(): Int = 42
            """.trimIndent(),
        )
    }

    @Test
    fun `reverse-ordered chain of top-level functions`() {
        assertBoxOk(
            """
            fun box(): String {
                val res = step1(1)
                return if (res == 16) "OK" else "FAIL"
            }

            fun step1(x: Int): Int = step2(x + 1)
            fun step2(x: Int): Int = step3(x + 2)
            fun step3(x: Int): Int = step4(x + 3)
            fun step4(x: Int): Int = step5(x + 4)
            fun step5(x: Int): Int = x + 5
            """.trimIndent(),
        )
    }

    @Test
    fun `mutual recursion between two top-level functions`() {
        assertBoxOk(
            """
            fun box(): String {
                val ok = isEven(10) && !isEven(7) && isOdd(7) && !isOdd(10)
                return if (ok) "OK" else "FAIL"
            }

            fun isEven(n: Int): Boolean =
                if (n == 0) true else isOdd(n - 1)

            fun isOdd(n: Int): Boolean =
                if (n == 0) false else isEven(n - 1)
            """.trimIndent(),
        )
    }

    @Test
    fun `three-way cyclic mutual recursion`() {
        assertBoxOk(
            """
            fun box(): String {
                val res = cycleA(7, 0)
                // 7 -> A(+1) -> B(+2) -> C(+3) -> A(+1) -> B(+2) -> C(+3) -> A(+1) -> stop
                // 1 + 2 + 3 + 1 + 2 + 3 + 1 = 13
                return if (res == 13) "OK" else "FAIL"
            }

            fun cycleA(n: Int, acc: Int): Int =
                if (n <= 0) acc else cycleB(n - 1, acc + 1)

            fun cycleB(n: Int, acc: Int): Int =
                if (n <= 0) acc else cycleC(n - 1, acc + 2)

            fun cycleC(n: Int, acc: Int): Int =
                if (n <= 0) acc else cycleA(n - 1, acc + 3)
            """.trimIndent(),
        )
    }

    @Test
    fun `top-level val initialized by function declared later`() {
        assertBoxOk(
            """
            val eagerValue: Int = computeInitial(20)

            fun box(): String {
                return if (eagerValue == 42) "OK" else "FAIL"
            }

            fun computeInitial(x: Int): Int = x * 2 + 2
            """.trimIndent(),
        )
    }

    @Test
    fun `top-level val initialized via chain of later functions`() {
        assertBoxOk(
            """
            val greeting: String = makeGreeting()

            fun box(): String = greeting

            fun makeGreeting(): String = leftPart() + rightPart()

            fun leftPart(): String = "O"
            fun rightPart(): String = "K"
            """.trimIndent(),
        )
    }

    @Test
    fun `top-level val initialized by mutually recursive functions declared later`() {
        assertBoxOk(
            """
            val checkEven: Boolean = isEven(6)
            val checkOdd: Boolean = isOdd(6)

            fun box(): String {
                return if (checkEven && !checkOdd) "OK" else "FAIL"
            }

            fun isEven(n: Int): Boolean =
                if (n == 0) true else isOdd(n - 1)

            fun isOdd(n: Int): Boolean =
                if (n == 0) false else isEven(n - 1)
            """.trimIndent(),
        )
    }

    @Test
    fun `interleaved top-level val and functions referencing earlier val and later functions`() {
        assertBoxOk(
            """
            val base: Int = 10
            val derived: Int = addWithTail(5)

            fun box(): String {
                return if (derived == 42) "OK" else "FAIL"
            }

            fun addWithTail(v: Int): Int = base + v + tailBonus()

            fun tailBonus(): Int = 27
            """.trimIndent(),
        )
    }

    @Test
    fun `top-level var initialized by later function and mutated by another later function`() {
        assertBoxOk(
            """
            var counter: Int = initialCount()

            fun box(): String {
                incrementCounter()
                incrementCounter()
                return if (counter == 12) "OK" else "FAIL"
            }

            fun initialCount(): Int = 10

            fun incrementCounter() {
                counter = counter + 1
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `local function calls top-level function declared later`() {
        assertBoxOk(
            """
            fun box(): String {
                fun inner(x: Int): Int {
                    return lateHelper(x) + 1
                }
                return if (inner(40) == 42) "OK" else "FAIL"
            }

            fun lateHelper(v: Int): Int = veryLateHelper(v + 1)

            fun veryLateHelper(v: Int): Int = v
            """.trimIndent(),
        )
    }

    @Test
    fun `box calls later function with dead code in between`() {
        assertBoxOk(
            """
            fun box(): String = aliveLater()

            fun unusedFirst(): Int = unusedSecond() + 1
            fun unusedSecond(): Int = 99

            fun aliveLater(): String = aliveLeaf()
            fun aliveLeaf(): String = "OK"
            """.trimIndent(),
        )
    }

    @Test
    fun `recursive local function`() {
        assertBoxOk(
            """
            fun box(): String {
                fun sumDown(n: Int): Int =
                    if (n <= 0) 0 else n + sumDown(n - 1)
                return if (sumDown(5) == 15) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }
}
