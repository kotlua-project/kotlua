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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IfWhenBoxTest : AbstractKotluaTest() {
    @Test
    fun `if statement with early return exits enclosing function directly`() {
        val code =
            """
            fun check(x: Int): String {
                if (x < 0) return "NEG"
                if (x == 0) return "ZERO"
                return "POS"
            }

            fun box(): String {
                if (check(-5) != "NEG") return "FAIL_NEG"
                if (check(0) != "ZERO") return "FAIL_ZERO"
                if (check(7) != "POS") return "FAIL_POS"
                return "OK"
            }
            """.trimIndent()

        assertBoxOk(code)
    }

    @Test
    fun `if statement in statement position does not emit unnecessary IIFE`() {
        val code =
            """
            fun test(x: Int): Int {
                if (x > 0) return 1
                return 0
            }
            """.trimIndent()

        val luaCode = compileToLua(code)
        assertTrue(luaCode.contains("if x > 0 then"), "Expected direct 'if' statement in:\n$luaCode")
        assertFalse(luaCode.contains("function()"), "Statement 'if' should not be wrapped in IIFE:\n$luaCode")
    }

    @Test
    fun `if-else-if-else statement mutates outer variable only in matching branch`() {
        val code =
            """
            fun classify(n: Int): String {
                var res = "init"
                if (n < 0) {
                    res = "negative"
                } else if (n == 0) {
                    res = "zero"
                } else if (n < 10) {
                    res = "small"
                } else {
                    res = "large"
                }
                return res
            }

            fun box(): String {
                if (classify(-1) != "negative") return "FAIL_1"
                if (classify(0) != "zero") return "FAIL_2"
                if (classify(5) != "small") return "FAIL_3"
                if (classify(42) != "large") return "FAIL_4"
                return "OK"
            }
            """.trimIndent()

        assertBoxOk(code)
    }

    @Test
    fun `if expression safely returns false and null without Lua and-or ternary bug`() {
        val code =
            """
            fun pickBool(cond: Boolean): Boolean =
                if (cond) false else true

            fun pickNullable(cond: Boolean): String? =
                if (cond) null else "value"

            fun box(): String {
                if (pickBool(true) != false) return "FAIL_BOOL_TRUE"
                if (pickBool(false) != true) return "FAIL_BOOL_FALSE"

                if (pickNullable(true) != null) return "FAIL_NULL_TRUE"
                if (pickNullable(false) != "value") return "FAIL_NULL_FALSE"
                return "OK"
            }
            """.trimIndent()

        assertBoxOk(code)
    }

    @Test
    fun `if expression handles multi-statement blocks and nested calls`() {
        val code =
            """
            fun add(a: Int, b: Int): Int = a + b

            fun compute(flag: Boolean, x: Int): Int {
                val res = if (flag) {
                    val doubled = x * 2
                    doubled + 1
                } else {
                    val tripled = x * 3
                    tripled - 1
                }
                return add(res, if (x > 0) 10 else -10)
            }

            fun box(): String {
                // (5 * 2 + 1) + 10 = 21
                if (compute(true, 5) != 21) return "FAIL_TRUE"
                // (-2 * 3 - 1) - 10 = -17
                if (compute(false, -2) != -17) return "FAIL_FALSE"
                return "OK"
            }
            """.trimIndent()

        assertBoxOk(code)
    }

    @Test
    fun `when without subject works as both expression and statement`() {
        val code =
            """
            fun grade(score: Int): String = when {
                score >= 90 -> "A"
                score >= 75 -> "B"
                score >= 50 -> "C"
                else -> "F"
            }

            fun box(): String {
                var sideEffect = ""
                when {
                    1 > 2 -> sideEffect = "bad"
                    2 == 2 -> sideEffect = "good"
                    else -> sideEffect = "else"
                }

                if (sideEffect != "good") return "FAIL_STMT"
                if (grade(95) != "A") return "FAIL_A"
                if (grade(80) != "B") return "FAIL_B"
                if (grade(50) != "C") return "FAIL_C"
                if (grade(10) != "F") return "FAIL_F"
                return "OK"
            }
            """.trimIndent()

        assertBoxOk(code)
    }

    @Test
    fun `when with subject supports single and multi-condition branches`() {
        val code =
            """
            fun describeInt(x: Int): String = when (x) {
                0 -> "zero"
                1, 2, 3 -> "small"
                else -> "other"
            }

            fun describeStr(s: String): Int = when (s) {
                "a", "b" -> 1
                "c" -> 2
                else -> 0
            }

            fun box(): String {
                if (describeInt(0) != "zero") return "FAIL_0"
                if (describeInt(1) != "small") return "FAIL_1"
                if (describeInt(2) != "small") return "FAIL_2"
                if (describeInt(3) != "small") return "FAIL_3"
                if (describeInt(9) != "other") return "FAIL_9"

                if (describeStr("a") != 1) return "FAIL_A"
                if (describeStr("b") != 1) return "FAIL_B"
                if (describeStr("c") != 2) return "FAIL_C"
                if (describeStr("z") != 0) return "FAIL_Z"
                return "OK"
            }
            """.trimIndent()

        assertBoxOk(code)
    }

    @Test
    fun `short-circuiting and-or avoids evaluating right-hand side unnecessarily`() {
        val code =
            """
            fun failIfCalled(tag: String): Boolean {
                lua("error('Short-circuit failed: ' .. tag)")
                return true
            }

            fun returnsTrue(): Boolean = true
            fun returnsFalse(): Boolean = false

            fun box(): String {
                val r1 = false && failIfCalled("AND")
                if (r1 != false) return "FAIL_AND_SHORT_CIRCUIT"

                val r2 = true || failIfCalled("OR")
                if (r2 != true) return "FAIL_OR_SHORT_CIRCUIT"

                val r3 = true && returnsFalse()
                if (r3 != false) return "FAIL_AND_EVAL"

                val r4 = false || returnsTrue()
                if (r4 != true) return "FAIL_OR_EVAL"

                return "OK"
            }
            """.trimIndent()

        assertBoxOk(code)
    }

    @Test
    fun `recursive functions using if-else evaluate properly`() {
        val code =
            """
            fun fib(n: Int): Int =
                if (n <= 1) n else fib(n - 1) + fib(n - 2)

            fun fact(n: Int): Int {
                if (n <= 1) return 1
                return n * fact(n - 1)
            }

            fun box(): String {
                if (fib(0) != 0) return "FAIL_FIB0"
                if (fib(7) != 13) return "FAIL_FIB7"
                if (fact(5) != 120) return "FAIL_FACT5"
                return "OK"
            }
            """.trimIndent()

        assertBoxOk(code)
    }
}
