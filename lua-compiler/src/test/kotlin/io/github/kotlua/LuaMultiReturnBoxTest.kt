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

class LuaMultiReturnBoxTest : AbstractKotluaTest() {
    @Test
    fun `basic destructuring and partial underscore destructuring with Pair`() {
        assertBoxOk(
            """
            import kotlin.lua.LuaMultiReturn

            @LuaMultiReturn
            private fun divMod(a: Int, b: Int): Pair<Int, Int> = Pair(a / b, a % b)

            fun box(): String {
                val (q1, r1) = divMod(17, 5)
                val (_, r2) = divMod(20, 6)
                val (q3, _) = divMod(25, 4)

                val ok1 = q1 == 3 && r1 == 2
                val ok2 = r2 == 2
                val ok3 = q3 == 6

                return if (ok1 && ok2 && ok3) "OK" else "FAIL:" + q1 + ":" + r1 + "|" + r2 + "|" + q3
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `automatic packing into object when captured as a whole or passed as argument`() {
        assertBoxOk(
            """
            import kotlin.lua.LuaMultiReturn

            @LuaMultiReturn
            private fun getEntry(id: Int): Pair<String, Int> = Pair("item_" + id, id * 10)

            private fun inspect(p: Pair<String, Int>): String = p.first + "=" + p.second

            fun box(): String {
                val packed = getEntry(5)
                val pOk = packed.first == "item_5" && packed.second == 50

                val firstName = getEntry(7).first
                val secondVal = getEntry(7).second

                val passedStr = inspect(getEntry(3))

                val ok = pOk &&
                    firstName == "item_7" &&
                    secondVal == 70 &&
                    passedStr == "item_3=30"

                return if (ok) "OK" else "FAIL:" + packed + "|" + firstName + "|" + secondVal + "|" + passedStr
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `custom data classes with three and four components`() {
        assertBoxOk(
            """
            import kotlin.lua.LuaMultiReturn

            data class RgbColor(val r: Int, val g: Int, val b: Int)
            data class PlayerRecord(val id: Int, val name: String, val isVip: Boolean, val score: Double)

            @LuaMultiReturn
            private fun unpackColor(hex: Int): RgbColor =
                RgbColor(hex / 65536, (hex / 256) % 256, hex % 256)

            @LuaMultiReturn
            private fun fetchPlayer(id: Int): PlayerRecord =
                PlayerRecord(id, "Player_" + id, id % 2 == 0, id * 1.5)

            fun box(): String {
                val (red, green, blue) = unpackColor(0x123456)

                val (pId, _, isVip, score) = fetchPlayer(42)

                val full = fetchPlayer(7)

                val colorOk = red == 0x12 && green == 0x34 && blue == 0x56
                val playerOk = pId == 42 && isVip && score == 63.0
                val fullOk = full.id == 7 && full.name == "Player_7" && !full.isVip && full.score == 10.5

                return if (colorOk && playerOk && fullOk) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `multiple branches and early returns inside LuaMultiReturn function`() {
        assertBoxOk(
            """
            import kotlin.lua.LuaMultiReturn

            @LuaMultiReturn
            private fun classify(value: Int): Pair<Boolean, String> {
                if (value == 0) return Pair(false, "zero")
                if (value < 0) return Pair(false, "negative")
                if (value > 100) return Pair(false, "overflow")
                return Pair(true, "valid:" + value)
            }

            fun box(): String {
                val (ok0, msg0) = classify(0)
                val (okNeg, msgNeg) = classify(-5)
                val (okOver, msgOver) = classify(150)
                val (okPos, msgPos) = classify(42)

                val ok = !ok0 && msg0 == "zero" &&
                    !okNeg && msgNeg == "negative" &&
                    !okOver && msgOver == "overflow" &&
                    okPos && msgPos == "valid:42"

                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `forwarding between LuaMultiReturn functions`() {
        assertBoxOk(
            """
            import kotlin.lua.LuaMultiReturn

            @LuaMultiReturn
            private fun source(a: Int, b: Int): Pair<Int, Int> = Pair(a * 2, b * 3)

            @LuaMultiReturn
            private fun forwardDirect(a: Int, b: Int): Pair<Int, Int> = source(a, b)

            @LuaMultiReturn
            private fun forwardWithTransform(a: Int, b: Int): Pair<Int, Int> {
                val (x, y) = source(a, b)
                return Pair(x + 1, y + 1)
            }

            fun box(): String {
                val (d1, d2) = forwardDirect(3, 4)
                val (t1, t2) = forwardWithTransform(3, 4)

                val ok = d1 == 6 && d2 == 12 && t1 == 7 && t2 == 13
                return if (ok) "OK" else "FAIL:" + d1 + ":" + d2 + "|" + t1 + ":" + t2
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `destructuring in loops and state progression`() {
        assertBoxOk(
            """
            import kotlin.lua.LuaMultiReturn

            @LuaMultiReturn
            private fun nextStep(step: Int): Pair<Int, Boolean> {
                val nextVal = step * 2 + 1
                val hasNext = nextVal < 50
                return Pair(nextVal, hasNext)
            }

            fun box(): String {
                var acc = 0
                var current = 1
                var iterations = 0

                while (true) {
                    iterations++
                    val (v, canContinue) = nextStep(current)
                    acc += v
                    current = v
                    if (!canContinue) break
                }

                // 1 -> (3, true) -> acc=3
                // 3 -> (7, true) -> acc=10
                // 7 -> (15, true) -> acc=25
                // 15 -> (31, true) -> acc=56
                // 31 -> (63, false) -> acc=119, break
                return if (acc == 119 && iterations == 5 && current == 63) "OK" else "FAIL:" + acc + ":" + iterations
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `non-data class with manual component operator functions`() {
        assertBoxOk(
            """
            import kotlin.lua.LuaMultiReturn

            class Vector2D(val x: Double, val y: Double) {
                operator fun component1(): Double = x
                operator fun component2(): Double = y
            }

            @LuaMultiReturn
            private fun createVector(angleDegrees: Double, length: Double): Vector2D =
                Vector2D(angleDegrees * 2.0, length * 3.0)

            fun box(): String {
                val (vx, vy) = createVector(10.0, 5.0)
                val vec = createVector(1.0, 2.0)

                val destructureOk = vx == 20.0 && vy == 15.0
                val packOk = vec.x == 2.0 && vec.y == 6.0

                return if (destructureOk && packOk) "OK" else "FAIL:" + vx + ":" + vy
            }
            """.trimIndent(),
        )
    }
}
