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

class ExceptionsBoxTest : AbstractKotluaTest() {
    @Test
    fun `multiple catch blocks with type hierarchy matching and unhandled propagation`() {
        assertBoxOk(
            """
            open class BaseAppError(val msg: String) : Throwable()
            class SpecificError(val code: Int) : BaseAppError("specific")
            class AlternativeError(val tag: String) : Throwable()
            class UnhandledError : Throwable()

            private fun dispatch(kind: Int): String {
                try {
                    when (kind) {
                        1 -> throw SpecificError(404)
                        2 -> throw BaseAppError("generic_fail")
                        3 -> throw AlternativeError("alt_tag")
                        4 -> throw UnhandledError()
                        else -> return "none"
                    }
                } catch (e: SpecificError) {
                    return "specific:" + e.code
                } catch (e: BaseAppError) {
                    return "base:" + e.msg
                } catch (e: AlternativeError) {
                    return "alt:" + e.tag
                }
            }

            fun box(): String {
                val r1 = dispatch(1)
                val r2 = dispatch(2)
                val r3 = dispatch(3)
                val rNone = dispatch(0)

                var outerCaught = false
                try {
                    dispatch(4)
                } catch (e: UnhandledError) {
                    outerCaught = true
                }

                val allOk = r1 == "specific:404" &&
                    r2 == "base:generic_fail" &&
                    r3 == "alt:alt_tag" &&
                    rNone == "none" &&
                    outerCaught

                return if (allOk) "OK" else "FAIL:" + r1 + "|" + r2 + "|" + r3 + "|" + outerCaught
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `try-finally execution order on normal, caught, and rethrown paths`() {
        assertBoxOk(
            """
            open class TestEx(val msg: String) : Throwable()
            class InnerEx(msg: String) : TestEx(msg)
            class RethrownEx(msg: String) : TestEx(msg)
            class FatalEx(msg: String) : Throwable()

            private var trace = ""

            private fun runScenario(scenario: Int) {
                try {
                    trace += "T1["
                    try {
                        trace += "T2["
                        if (scenario == 1) return // early return inside nested try
                        if (scenario == 2) throw InnerEx("err2")
                        if (scenario == 3) throw FatalEx("err3")
                        trace += "ok"
                    } catch (e: InnerEx) {
                        trace += "C2(" + e.msg + ")"
                        if (scenario == 2) throw RethrownEx(e.msg)
                    } finally {
                        trace += "]F2,"
                    }
                    trace += "]T1_norm,"
                } catch (e: RethrownEx) {
                    trace += "C1_re(" + e.msg + ")"
                } catch (e: FatalEx) {
                    trace += "C1_fatal(" + e.message + ")"
                } finally {
                    trace += "]F1;"
                }
            }

            fun box(): String {
                trace = ""
                runScenario(0) // normal
                runScenario(1) // early return
                runScenario(2) // caught and rethrown
                runScenario(3) // unhandled by inner catch

                val expected =
                    "T1[T2[ok]F2,]T1_norm,]F1;" +
                    "T1[T2[]F2,]F1;" +
                    "T1[T2[C2(err2)]F2,C1_re(err2)]F1;" +
                    "T1[T2[]F2,C1_fatal(null)]F1;"

                return if (trace == expected) "OK" else "FAIL:" + trace
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `try-catch-finally inside loops with labeled break and continue`() {
        assertBoxOk(
            """
            class SkipException : Throwable()
            class AbortException : Throwable()

            fun box(): String {
                var trace = ""
                var i = 0

                outer@ while (i < 3) {
                    i++
                    try {
                        trace += "O" + i + "("
                        var j = 0
                        while (j < 3) {
                            j++
                            try {
                                trace += "I" + j
                                if (i == 1 && j == 2) {
                                    throw SkipException()
                                }
                                if (i == 2 && j == 1) {
                                    throw AbortException()
                                }
                                if (i == 3 && j == 1) {
                                    continue
                                }
                                if (i == 3 && j == 2) {
                                    break
                                }
                                trace += ","
                            } catch (e: SkipException) {
                                trace += "skip,"
                                continue@outer
                            } catch (e: AbortException) {
                                trace += "abort,"
                                break@outer
                            } finally {
                                trace += "fI|"
                            }
                        }
                    } finally {
                        trace += ")fO;"
                    }
                }

                // i=1: j=1 normal (I1,fI|), j=2 throws Skip (I2skip,fI|), then outer finally )fO;
                // i=2: j=1 throws Abort (I1abort,fI|), then outer finally )fO; loop breaks completely
                val expected = "O1(I1,fI|I2skip,fI|)fO;O2(I1abort,fI|)fO;"

                return if (trace == expected && i == 2) "OK" else "FAIL:" + i + ":" + trace
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `non-local return from try and catch across inline lambdas with nested finally`() {
        assertBoxOk(
            """
            private inline fun <T> myApply(block: () -> T): T = block()

            class Recoverable(val reason: String) : Throwable()

            private var log = ""

            private fun execute(action: Int): String {
                try {
                    log += "out_try["
                    myApply {
                        try {
                            log += "in_try["
                            if (action == 1) return "from_try"
                            if (action == 2) throw Recoverable("rec_flag")
                            log += "norm"
                        } catch (e: Recoverable) {
                            log += "catch(" + e.reason + ")["
                            if (action == 2) return "from_catch"
                        } finally {
                            log += "]in_fin,"
                        }
                        "apply_res"
                    }
                } finally {
                    log += "]out_fin;"
                }
                return "from_end"
            }

            fun box(): String {
                log = ""
                val r1 = execute(1)
                val r2 = execute(2)
                val r3 = execute(3)

                val expectedLog =
                    "out_try[in_try[]in_fin,]out_fin;" +
                    "out_try[in_try[catch(rec_flag)[]in_fin,]out_fin;" +
                    "out_try[in_try[norm]in_fin,]out_fin;"

                val ok = r1 == "from_try" &&
                    r2 == "from_catch" &&
                    r3 == "from_end" &&
                    log == expectedLog

                return if (ok) "OK" else "FAIL:" + r1 + ":" + r2 + ":" + r3 + ":" + log
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `try as expression inside arithmetic and assignment with finally side-effects`() {
        assertBoxOk(
            """
            class InvalidArg(val code: Int) : Throwable()
            class OverflowArg : Throwable()

            fun box(): String {
                var cleanups = 0

                fun compute(v: Int): Int =
                    100 + try {
                        when {
                            v < 0 -> throw InvalidArg(v)
                            v > 50 -> throw OverflowArg()
                            else -> v * 2
                        }
                    } catch (e: InvalidArg) {
                        e.code
                    } catch (e: OverflowArg) {
                        999
                    } finally {
                        cleanups++
                    }

                val a = compute(10)   // 100 + 20 = 120
                val b = compute(-15)  // 100 + (-15) = 85
                val c = compute(100)  // 100 + 999 = 1099

                return if (a == 120 && b == 85 && c == 1099 && cleanups == 3) {
                    "OK"
                } else {
                    "FAIL:" + a + ":" + b + ":" + c + ":" + cleanups
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `exceptions in non-inline lambdas and higher-order functions`() {
        assertBoxOk(
            """
            class LambdaError(val payload: String) : Throwable()

            private fun runSafe(action: (String) -> String, arg: String): String =
                try {
                    action(arg)
                } catch (e: LambdaError) {
                    "handled:" + e.payload
                }

            fun box(): String {
                val successfulLambda: (String) -> String = { it + "!" }
                val throwingLambda: (String) -> String = {
                    if (it.length > 3) throw LambdaError("too_long")
                    it
                }

                val r1 = runSafe(successfulLambda, "kotlua")
                val r2 = runSafe(throwingLambda, "hi")
                val r3 = runSafe(throwingLambda, "toolongstring")

                return if (r1 == "kotlua!" && r2 == "hi" && r3 == "handled:too_long") {
                    "OK"
                } else {
                    "FAIL:" + r1 + ":" + r2 + ":" + r3
                }
            }
            """.trimIndent(),
        )
    }
}
