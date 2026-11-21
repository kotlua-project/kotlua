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

class CompareToSemanticsBoxTest : AbstractKotluaTest() {
    @Test
    fun `primitive numeric compareTo including mixed types and Long`() {
        assertBoxOk(
            """
            fun box(): String {
                val ok =
                    1.compareTo(2) < 0 &&
                        2.compareTo(1) > 0 &&
                        2.compareTo(2) == 0 &&
                        1.compareTo(2.5) < 0 &&
                        3.5.compareTo(2) > 0 &&
                        10L.compareTo(5L) > 0 &&
                        5L.compareTo(5L) == 0
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nan compareTo uses total order unlike less-than operator`() {
        assertBoxOk(
            """
            fun box(): String {
                val nan1 = 0.0 / 0.0
                val nan2 = 0.0 / 0.0
                val inf = 1.0 / 0.0
                val ok =
                    !(nan1 < inf) &&
                        !(nan1 > inf) &&
                        nan1.compareTo(inf) > 0 &&
                        inf.compareTo(nan1) < 0 &&
                        nan1.compareTo(nan2) == 0
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    @EnabledIfSystemProperty(
        named = "kotlua.test.luaExecutable",
        matches = ".+",
        disabledReason = "LuaJ 3.0.1 collapses -0.0 into LuaInteger(0); requires native Lua 5.1 / LuaJIT",
    )
    fun `negative zero is less than positive zero in Double compareTo`() {
        assertBoxOk(
            """
            fun makeNegZero(): Double = -0.0
            fun makePosZero(): Double = 0.0

            fun box(): String {
                val negZero = makeNegZero()
                val posZero = makePosZero()
                val ok =
                    !(negZero < posZero) &&
                        negZero.compareTo(posZero) < 0 &&
                        posZero.compareTo(negZero) > 0 &&
                        negZero.compareTo(negZero) == 0
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `boolean string and char compareTo`() {
        assertBoxOk(
            """
            fun box(): String {
                val ok =
                    false.compareTo(true) < 0 &&
                        true.compareTo(false) > 0 &&
                        true.compareTo(true) == 0 &&
                        (false < true) &&
                        !(true < false) &&
                        'a'.compareTo('b') < 0 &&
                        'b'.compareTo('b') == 0 &&
                        "abc".compareTo("abd") < 0 &&
                        "abc" < "abd" &&
                        "xyz" > "abc"
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `enum and custom Comparable dispatch directly and via Comparable`() {
        assertBoxOk(
            """
            enum class Color { RED, GREEN, BLUE }

            open class Box(val x: Int) : Comparable<Box> {
                override fun compareTo(other: Box): Int = x.compareTo(other.x)

                operator fun compareTo(other: Int): Int = x.compareTo(other)
            }

            class SubBox(x: Int) : Box(x) {
                override fun compareTo(other: Box): Int = super.compareTo(other)
            }

            fun <T : Comparable<T>> cmp(a: T, b: T): Int = a.compareTo(b)

            fun box(): String {
                val c1: Comparable<Box> = Box(1)
                val c2: Comparable<Int> = 10

                val ok =
                    Color.RED < Color.BLUE &&
                        cmp(Color.GREEN, Color.RED) > 0 &&
                        Box(1) < Box(2) &&
                        Box(5).compareTo(5) == 0 &&
                        Box(5).compareTo(3) > 0 &&
                        c1.compareTo(Box(2)) < 0 &&
                        c2.compareTo(5) > 0 &&
                        cmp(SubBox(3), SubBox(2)) > 0 &&
                        cmp("a", "b") < 0 &&
                        cmp(false, true) < 0
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `data class implementing Comparable with multi-field comparison and generic sort`() {
        assertBoxOk(
            """
            data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
                override fun compareTo(other: Version): Int {
                    val m = major.compareTo(other.major)
                    if (m != 0) return m
                    val n = minor.compareTo(other.minor)
                    if (n != 0) return n
                    return patch.compareTo(other.patch)
                }
            }

            fun <T : Comparable<T>> sortArray(arr: Array<T>) {
                for (i in 0 until arr.size) {
                    for (j in i + 1 until arr.size) {
                        if (arr[j] < arr[i]) {
                            val tmp = arr[i]
                            arr[i] = arr[j]
                            arr[j] = tmp
                        }
                    }
                }
            }

            fun box(): String {
                val v1 = Version(2, 0, 0)
                val v2 = Version(1, 10, 0)
                val v3 = Version(1, 2, 5)
                val v4 = Version(1, 2, 1)

                val arr = arrayOf(v1, v2, v3, v4)
                sortArray(arr)

                val ok =
                    arr[0] == v4 &&
                        arr[1] == v3 &&
                        arr[2] == v2 &&
                        arr[3] == v1 &&
                        Version(1, 2, 5).compareTo(Version(1, 2, 5)) == 0
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `enum with properties and entry subclasses combined with data class Comparable`() {
        assertBoxOk(
            """
            enum class Priority(val weight: Int) {
                LOW(1) {
                    override fun tag(): String = "L"
                },
                MEDIUM(5) {
                    override fun tag(): String = "M"
                },
                HIGH(10) {
                    override fun tag(): String = "H"
                };

                abstract fun tag(): String
            }

            data class Task(val priority: Priority, val name: String) : Comparable<Task> {
                override fun compareTo(other: Task): Int {
                    val p = priority.compareTo(other.priority)
                    if (p != 0) return p
                    return name.compareTo(other.name)
                }
            }

            fun <T : Comparable<T>> maxItem(a: T, b: T): T = if (a >= b) a else b

            fun box(): String {
                val t1 = Task(Priority.LOW, "alpha")
                val t2 = Task(Priority.HIGH, "beta")
                val t3 = Task(Priority.HIGH, "alpha")

                val p: Comparable<Priority> = Priority.LOW
                val ok =
                    Priority.LOW < Priority.MEDIUM &&
                        Priority.HIGH > Priority.MEDIUM &&
                        p.compareTo(Priority.HIGH) < 0 &&
                        Priority.LOW.tag() == "L" &&
                        t1 < t3 &&
                        t3 < t2 &&
                        maxItem(t1, t2) == t2 &&
                        maxItem(t2, t3) == t2 &&
                        maxItem(Priority.LOW, Priority.HIGH) == Priority.HIGH
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `interface inheritance and default compareTo implementation`() {
        assertBoxOk(
            """
            interface Ranked : Comparable<Ranked> {
                val rank: Int

                override fun compareTo(other: Ranked): Int = rank.compareTo(other.rank)
            }

            interface NamedRanked : Ranked {
                val title: String
            }

            class Player(override val rank: Int, override val title: String) : NamedRanked

            class VipPlayer(rank: Int, title: String, val bonus: Int) : NamedRanked {
                override val rank: Int = rank
                override val title: String = title

                override fun compareTo(other: Ranked): Int {
                    if (other is VipPlayer) {
                        val base = super.compareTo(other)
                        if (base != 0) return base
                        return bonus.compareTo(other.bonus)
                    }
                    return super.compareTo(other)
                }
            }

            fun compareAnyRanked(a: Ranked, b: Ranked): Int = a.compareTo(b)
            fun compareViaNamed(a: NamedRanked, b: NamedRanked): Int = a.compareTo(b)

            fun box(): String {
                val p1 = Player(10, "A")
                val p2 = Player(20, "B")
                val v1 = VipPlayer(20, "V1", 1)
                val v2 = VipPlayer(20, "V2", 5)

                val ok =
                    p1 < p2 &&
                        compareAnyRanked(p1, p2) < 0 &&
                        compareViaNamed(p2, p1) > 0 &&
                        compareViaNamed(v1, v2) < 0 &&
                        v2 > p1
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `sealed hierarchy and inherited compareTo kept by DCE when called only via Comparable`() {
        assertBoxOk(
            """
            sealed class Node(val weight: Int) : Comparable<Node> {
                override fun compareTo(other: Node): Int = weight.compareTo(other.weight)

                class Leaf(weight: Int) : Node(weight)

                data class Branch(val left: Int, val right: Int) : Node(left + right)
            }

            class SpecializedLeaf(weight: Int, val tieBreaker: Int) : Node(weight) {
                override fun compareTo(other: Node): Int {
                    val w = super.compareTo(other)
                    if (w != 0) return w
                    return if (other is SpecializedLeaf) tieBreaker.compareTo(other.tieBreaker) else 0
                }
            }

            fun checkOrder(a: Comparable<Node>, b: Node): Int = a.compareTo(b)

            fun box(): String {
                val leaf: Node = Node.Leaf(5)
                val branch: Node = Node.Branch(3, 4)
                val s1: Node = SpecializedLeaf(5, 1)
                val s2: Node = SpecializedLeaf(5, 2)

                val ok =
                    checkOrder(leaf, branch) < 0 &&
                        checkOrder(branch, leaf) > 0 &&
                        checkOrder(s1, s2) < 0 &&
                        checkOrder(s2, s1) > 0 &&
                        checkOrder(leaf, s1) == 0
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `object and inherited non-overridden compareTo`() {
        assertBoxOk(
            """
            open class BaseScore(val value: Int) : Comparable<BaseScore> {
                override fun compareTo(other: BaseScore): Int = value.compareTo(other.value)
            }

            class DerivedScore(value: Int) : BaseScore(value)

            object ZeroScore : BaseScore(0)

            fun <T : Comparable<T>> isOrdered(a: T, b: T): Boolean = a < b

            fun box(): String {
                val d1 = DerivedScore(3)
                val d2 = DerivedScore(7)

                val ok =
                    ZeroScore < d1 &&
                        d1 < d2 &&
                        isOrdered<BaseScore>(ZeroScore, d1) &&
                        isOrdered(d1, d2)
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }
}
