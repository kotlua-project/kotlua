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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import kotlin.math.*

class MathAndNumbersBoxTest : AbstractKotluaTest() {
    @Test
    fun `Double and Float trig exp log and rounding`() =
        assertBoxOk(
            """
            import kotlin.math.*

            fun near(a: Double, b: Double, eps: Double = 1e-9): Boolean =
                abs(a - b) <= eps

            fun box(): String {
                if (!near(sin(PI / 2.0), 1.0) || !near(cos(0.0), 1.0) || !near(tan(PI / 4.0), 1.0)) {
                    return "FAIL: trig"
                }
                if (!sin(Double.NaN).isNaN() || !sin(Double.POSITIVE_INFINITY).isNaN() || !cos(Double.NEGATIVE_INFINITY).isNaN()) {
                    return "FAIL: trig special"
                }
                if (!near(asin(1.0), PI / 2.0) || !near(acos(1.0), 0.0) || !near(atan(1.0), PI / 4.0)) {
                    return "FAIL: inv trig"
                }
                if (!asin(1.5).isNaN() || !acos(-1.5).isNaN()) return "FAIL: inv trig domain"
                if (!near(atan2(1.0, -1.0), 3.0 * PI / 4.0)) return "FAIL: atan2"

                if (!near(sinh(0.0), 0.0) || !near(cosh(0.0), 1.0) || !near(tanh(0.0), 0.0)) {
                    return "FAIL: hyp"
                }
                if (tanh(Double.POSITIVE_INFINITY) != 1.0 || tanh(Double.NEGATIVE_INFINITY) != -1.0) {
                    return "FAIL: tanh inf"
                }
                if (!near(asinh(sinh(1.2)), 1.2) || !near(acosh(cosh(1.5)), 1.5) || !near(atanh(tanh(0.7)), 0.7)) {
                    return "FAIL: inv hyp"
                }
                if (!acosh(0.5).isNaN() || atanh(1.0) != Double.POSITIVE_INFINITY || atanh(-1.0) != Double.NEGATIVE_INFINITY) {
                    return "FAIL: inv hyp special"
                }

                if (!near(hypot(3.0, 4.0), 5.0)) return "FAIL: hypot"
                if (hypot(Double.NaN, Double.POSITIVE_INFINITY) != Double.POSITIVE_INFINITY) {
                    return "FAIL: hypot inf+nan"
                }

                if (!near(exp(1.0), E) || !near(expm1(1e-12), 1e-12, 1e-20)) return "FAIL: exp"
                if (!near(ln(E), 1.0) || !near(log10(1000.0), 3.0) || !near(log2(8.0), 3.0) || !near(log(81.0, 3.0), 4.0)) {
                    return "FAIL: log"
                }
                if (!near(ln1p(1e-12), 1e-12, 1e-20) || ln1p(-1.0) != Double.NEGATIVE_INFINITY) {
                    return "FAIL: ln1p"
                }
                if (!near(cbrt(27.0), 3.0) || !near(cbrt(-27.0), -3.0)) return "FAIL: cbrt"
                if (2.0.pow(10) != 1024.0 || Double.NaN.pow(0.0) != 1.0 || 1.0.pow(Double.POSITIVE_INFINITY).isNaN().not()) {
                    return "FAIL: pow"
                }

                if (ceil(1.2) != 2.0 || floor(1.8) != 1.0 || truncate(-1.8) != -1.0) return "FAIL: ceil/floor/trunc"
                if (round(0.5) != 0.0 || round(1.5) != 2.0 || round(2.5) != 2.0 || round(-1.5) != -2.0 || round(-2.5) != -2.0) {
                    return "FAIL: round"
                }
                if (0.5.roundToInt() != 1 || (-0.5).roundToInt() != 0 || (-1.5).roundToInt() != -1) {
                    return "FAIL: roundToInt"
                }
                if (Double.POSITIVE_INFINITY.roundToInt() != Int.MAX_VALUE || Double.NEGATIVE_INFINITY.roundToInt() != Int.MIN_VALUE) {
                    return "FAIL: roundToInt clamp"
                }
                try {
                    Double.NaN.roundToInt()
                    return "FAIL: roundToInt NaN"
                } catch (_: IllegalArgumentException) {
                }

                if (!near(sin(0.5f).toDouble(), sin(0.5), 1e-5)) return "FAIL: float sin"
                if (min(1.0f, Float.NaN).isNaN().not() || max(1.0f, Float.NaN).isNaN().not()) return "FAIL: float NaN"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `ulp nextUp nextDown nextTowards and IEEE-754 bits`() =
        assertBoxOk(
            """
            import kotlin.math.*

            fun box(): String {
                if (Double.NaN.ulp.isNaN().not() || Double.POSITIVE_INFINITY.ulp != Double.POSITIVE_INFINITY) {
                    return "FAIL: ulp special"
                }
                if (0.0.ulp != Double.MIN_VALUE) return "FAIL: 0.ulp"
                if (1.0.nextUp() != 1.0 + 1.0.ulp) return "FAIL: 1.0.nextUp"
                if (1.0.nextUp().nextDown() != 1.0) return "FAIL: nextUp/nextDown"
                if ((-1.0).nextUp() >= -1.0 && false) return "FAIL: unreachable"
                if ((-1.0).nextUp().nextDown() != -1.0) return "FAIL: -1.0 nextUp/nextDown"
                if (1.0.nextTowards(2.0) != 1.0.nextUp() || 1.0.nextTowards(0.0) != 1.0.nextDown()) {
                    return "FAIL: nextTowards"
                }

                if (1.0.toBits() != 0x3ff0000000000000L || Double.fromBits(0x3ff0000000000000L) != 1.0) {
                    return "FAIL: Double bits"
                }
                if (Double.POSITIVE_INFINITY.toBits() != 0x7ff0000000000000L) return "FAIL: Inf bits"
                if (Double.fromBits(0x7ff8000000000000L).isNaN().not()) return "FAIL: Double fromBits NaN"
                if (Double.MIN_VALUE.toBits() != 1L || Double.fromBits(1L) != Double.MIN_VALUE) {
                    return "FAIL: Double MIN_VALUE bits"
                }

                if (1.0f.toBits() != 0x3f800000 || Float.fromBits(0x3f800000) != 1.0f) {
                    return "FAIL: Float bits"
                }
                if ((-2.5f).toBits() != -1071644672 || Float.fromBits(-1071644672) != -2.5f) {
                    return "FAIL: Float -2.5 bits"
                }
                if (Float.fromBits(0x7f800000) != Float.POSITIVE_INFINITY) return "FAIL: Float Inf bits"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    @EnabledIfSystemProperty(named = "kotlua.test.luaExecutable", matches = ".+")
    fun `negative zero math and bits semantics`() =
        assertBoxOk(
            """
            import kotlin.math.*

            fun isNegZero(x: Double): Boolean = x == 0.0 && 1.0 / x < 0.0
            
            val posZero = 1.0 - 1.0
            val negZero = -1.0 / Double.POSITIVE_INFINITY

            fun box(): String {
                if (!isNegZero(sign(negZero))) return "FAIL: sign(-0.0)"
                if (!isNegZero(sqrt(negZero))) return "FAIL: sqrt(-0.0)"
                if (!isNegZero(ceil(-0.5))) return "FAIL: ceil(-0.5)"
                if (!isNegZero(round(-0.5))) return "FAIL: round(-0.5)"
                if (isNegZero(abs(negZero))) return "FAIL: abs(-0.0)"
                if (!isNegZero(min(posZero, negZero)) || isNegZero(max(negZero, posZero))) return "FAIL: min/max -0.0"
                if (!isNegZero(posZero.withSign(negZero))) return "FAIL: withSign -0.0"
                if (!isNegZero(posZero.nextTowards(negZero))) return "FAIL: nextTowards -0.0"
                if (!isNegZero((-Double.MIN_VALUE).nextUp())) return "FAIL: -MIN_VALUE.nextUp"
                if ((negZero).toBits() != Long.MIN_VALUE || !isNegZero(Double.fromBits(Long.MIN_VALUE))) {
                    return "FAIL: -0.0 Double bits"
                }
                if ((-0.0f).toBits() != Int.MIN_VALUE) return "FAIL: -0.0f Float bits"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `Int and Long math and bit operations`() =
        assertBoxOk(
            """
            import kotlin.math.*

            fun box(): String {
                if (abs(-42) != 42 || abs(Int.MIN_VALUE) != Int.MIN_VALUE) return "FAIL: Int.abs"
                if (42.absoluteValue != 42 || (-5).sign != -1 || 0.sign != 0 || 5.sign != 1) return "FAIL: Int.sign"
                if (min(3, 7) != 3 || max(3, 7) != 7) return "FAIL: Int min/max"

                if (0.countOneBits() != 0 || (-1).countOneBits() != 32 || 0x12345678.countOneBits() != 13) {
                    return "FAIL: Int.countOneBits"
                }
                if (0.countLeadingZeroBits() != 32 || 1.countLeadingZeroBits() != 31 || (-1).countLeadingZeroBits() != 0) {
                    return "FAIL: Int.clz"
                }
                if (0.countTrailingZeroBits() != 32 || 16.countTrailingZeroBits() != 4 || 1.countTrailingZeroBits() != 0) {
                    return "FAIL: Int.ctz"
                }
                if (0.takeHighestOneBit() != 0 || 10.takeHighestOneBit() != 8 || (-1).takeHighestOneBit() != Int.MIN_VALUE) {
                    return "FAIL: Int.highest"
                }
                if (0.takeLowestOneBit() != 0 || 12.takeLowestOneBit() != 4) return "FAIL: Int.lowest"
                if (1.rotateLeft(4) != 16 || 1.rotateLeft(-1) != Int.MIN_VALUE || 16.rotateRight(4) != 1) {
                    return "FAIL: Int.rotate"
                }

                if (abs((-42).toLong()) != 42L || abs(Long.MIN_VALUE) != Long.MIN_VALUE) return "FAIL: Long.abs"
                if ((-5).toLong().sign != -1 || 0L.sign != 0 || 5L.sign != 1) return "FAIL: Long.sign"
                if (0L.countOneBits() != 0 || (-1).toLong().countOneBits() != 64 || 0xFFL.countOneBits() != 8) {
                    return "FAIL: Long.countOneBits"
                }
                if (0L.countLeadingZeroBits() != 64 || 1L.countLeadingZeroBits() != 63 || (-1).toLong().countLeadingZeroBits() != 0) {
                    return "FAIL: Long.clz"
                }
                if (0L.countTrailingZeroBits() != 64 || 16L.countTrailingZeroBits() != 4) return "FAIL: Long.ctz"
                if (10L.takeHighestOneBit() != 8L || 12L.takeLowestOneBit() != 4L) return "FAIL: Long.bits"
                if (1L.rotateLeft(40) != 1099511627776L || 1099511627776L.rotateRight(40) != 1L) {
                    return "FAIL: Long.rotate"
                }

                return "OK"
            }
            """.trimIndent(),
        )
}
