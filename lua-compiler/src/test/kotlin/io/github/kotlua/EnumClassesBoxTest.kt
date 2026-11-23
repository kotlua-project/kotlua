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

class EnumClassesBoxTest : AbstractKotluaTest() {
    @Test
    fun `basic enum properties ordinal name and reference equality`() {
        assertBoxOk(
            """
            enum class Direction {
                NORTH,
                EAST,
                SOUTH,
                WEST,
            }

            fun box(): String {
                val n = Direction.NORTH
                val e = Direction.EAST
                val s = Direction.SOUTH
                val w = Direction.WEST

                val names = n.name + "," + e.name + "," + s.name + "," + w.name
                val ordinals = n.ordinal.toString() + "," + e.ordinal + "," + s.ordinal + "," + w.ordinal
                val toStrings = n.toString() + ":" + w.toString()

                val same = (Direction.NORTH === n) && (n != e) && (s === Direction.SOUTH)
                val order = (n < e) && (e < s) && (s < w) && (n.compareTo(w) < 0)

                return if (names == "NORTH,EAST,SOUTH,WEST" &&
                    ordinals == "0,1,2,3" &&
                    toStrings == "NORTH:WEST" &&
                    same && order
                ) {
                    "OK"
                } else {
                    "FAIL:" + names + ":" + ordinals + ":" + same + ":" + order
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `enum with constructor arguments methods and interface implementation`() {
        assertBoxOk(
            """
            interface Operation {
                fun apply(a: Int, b: Int): Int
            }

            enum class MathOp(val symbol: String, val weight: Int) : Operation {
                PLUS("+", 1) {
                    override fun apply(a: Int, b: Int): Int = a + b
                },
                MINUS("-", 2) {
                    override fun apply(a: Int, b: Int): Int = a - b
                },
                TIMES("*", 3) {
                    override fun apply(a: Int, b: Int): Int = a * b
                };

                fun info(): String = symbol + ":" + weight
            }

            fun box(): String {
                val p = MathOp.PLUS
                val m = MathOp.MINUS
                val t = MathOp.TIMES

                val r1 = p.apply(10, 4)
                val r2 = m.apply(10, 4)
                val r3 = t.apply(10, 4)

                val i1 = p.info()
                val i2 = m.info()
                val i3 = t.info()

                val isOp = p is Operation
                val isEnum = p is MathOp

                return if (r1 == 14 && r2 == 6 && r3 == 40 &&
                    i1 == "+:1" && i2 == "-:2" && i3 == "*:3" &&
                    isOp && isEnum
                ) {
                    "OK"
                } else {
                    "FAIL:" + r1 + ":" + r2 + ":" + r3
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `exhaustive when on enum without else branch`() {
        assertBoxOk(
            """
            enum class Status {
                IDLE,
                RUNNING,
                FAILED,
                FINISHED,
            }

            private fun describe(s: Status): String =
                when (s) {
                    Status.IDLE -> "idle"
                    Status.RUNNING -> "run"
                    Status.FAILED -> "err"
                    Status.FINISHED -> "done"
                }

            fun box(): String {
                var counter = 0
                fun logStatus(s: Status) {
                    when (s) {
                        Status.IDLE -> counter += 1
                        Status.RUNNING -> counter += 10
                        Status.FAILED -> counter += 100
                        Status.FINISHED -> counter += 1000
                    }
                }

                val d1 = describe(Status.IDLE)
                val d2 = describe(Status.RUNNING)
                val d3 = describe(Status.FAILED)
                val d4 = describe(Status.FINISHED)

                logStatus(Status.RUNNING)
                logStatus(Status.FINISHED)

                return if (d1 == "idle" && d2 == "run" && d3 == "err" && d4 == "done" && counter == 1010) {
                    "OK"
                } else {
                    "FAIL:" + d1 + ":" + d2 + ":" + counter
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `enum values and valueOf methods`() {
        assertBoxOk(
            """
            enum class Color {
                RED,
                GREEN,
                BLUE,
            }

            fun box(): String {
                val vals = Color.values()
                var trace = ""
                var i = 0
                while (i < vals.size) {
                    val c = vals[i]
                    trace += c.name + ":" + c.ordinal + ","
                    i++
                }

                val fromName1 = Color.valueOf("GREEN")
                val fromName2 = Color.valueOf("BLUE")

                val okLookup = (fromName1 === Color.GREEN) && (fromName2 === Color.BLUE)

                return if (vals.size == 3 && trace == "RED:0,GREEN:1,BLUE:2," && okLookup) {
                    "OK"
                } else {
                    "FAIL:" + trace
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `enum entries property and enumEntries intrinsic`() {
        assertBoxOk(
            """
            import kotlin.enums.enumEntries

            enum class Color(val code: Int) {
                RED(1),
                GREEN(2) {
                    override fun label(): String = "G"
                },
                BLUE(3);

                open fun label(): String = name
            }

            fun box(): String {
                val entries = Color.entries
                val viaIntrinsic = enumEntries<Color>()

                if (entries !== viaIntrinsic) return "fail identity"
                if (entries.size != 3) return "fail size"
                if (entries[0] != Color.RED || entries[1] != Color.GREEN || entries[2] != Color.BLUE) {
                    return "fail order"
                }
                if (entries[1].label() != "G" || entries[0].code != 1) return "fail entry member"
                if (!entries.contains(Color.GREEN)) return "fail contains"
                if (entries.indexOf(Color.BLUE) != 2 || entries.lastIndexOf(Color.BLUE) != 2) {
                    return "fail indexOf"
                }

                var names = ""
                for (c in entries) {
                    names += c.name
                }
                if (names != "REDGREENBLUE") return "fail iteration"

                var oobCaught = false
                try {
                    entries[3]
                } catch (_: IndexOutOfBoundsException) {
                    oobCaught = true
                }
                if (!oobCaught) return "fail oob"

                return "OK"
            }
            """.trimIndent(),
        )
    }
}
