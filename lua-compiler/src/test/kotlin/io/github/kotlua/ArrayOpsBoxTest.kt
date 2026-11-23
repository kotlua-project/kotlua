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

class ArrayOpsBoxTest : AbstractKotluaTest() {
    @Test
    fun `isEmpty isNotEmpty lastIndex and indices`() {
        assertBoxOk(
            """
            fun box(): String {
                val empty = emptyArray<String>()
                val emptyInts = intArrayOf()
                val arr = arrayOf("a", "b", "c")
                val ints = intArrayOf(10, 20)

                val ok =
                    empty.isEmpty() &&
                        !empty.isNotEmpty() &&
                        empty.lastIndex == -1 &&
                        emptyInts.isEmpty() &&
                        !arr.isEmpty() &&
                        arr.isNotEmpty() &&
                        arr.lastIndex == 2 &&
                        arr.indices.first == 0 &&
                        arr.indices.last == 2 &&
                        ints.lastIndex == 1
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `contentEquals handles nulls null elements and NaN`() {
        assertBoxOk(
            """
            fun box(): String {
                val n1: IntArray? = null
                val n2: IntArray? = null
                val a1 = intArrayOf(1, 2, 3)
                val a2 = intArrayOf(1, 2, 3)
                val a3 = intArrayOf(1, 2, 4)

                val s1 = arrayOf<String?>("x", null, "y")
                val s2 = arrayOf<String?>("x", null, "y")
                val s3 = arrayOf<String?>("x", "z", "y")

                val d1 = doubleArrayOf(0.0 / 0.0, 1.5)
                val d2 = doubleArrayOf(0.0 / 0.0, 1.5)

                val ok =
                    (n1 contentEquals n2) &&
                        !(n1 contentEquals a1) &&
                        !(a1 contentEquals n1) &&
                        (a1 contentEquals a2) &&
                        !(a1 contentEquals a3) &&
                        (s1 contentEquals s2) &&
                        !(s1 contentEquals s3) &&
                        (d1 contentEquals d2)
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
    fun `doubleArray contentEquals distinguishes negative zero from positive zero`() {
        assertBoxOk(
            """
            fun makeNegZero(): Double = -0.0
            fun makePosZero(): Double = 0.0

            fun box(): String {
                val a = doubleArrayOf(makeNegZero())
                val b = doubleArrayOf(makePosZero())
                val c = doubleArrayOf(makeNegZero())
                val ok = !(a contentEquals b) && (a contentEquals c)
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `contentToString and contentHashCode`() {
        assertBoxOk(
            """
            fun box(): String {
                val n: IntArray? = null
                val ints = intArrayOf(1, 2, 3)
                val intsCopy = intArrayOf(1, 2, 3)
                val strs = arrayOf<String?>("a", null, "b")

                val ok =
                    n.contentToString() == "null" &&
                        n.contentHashCode() == 0 &&
                        ints.contentToString() == "[1, 2, 3]" &&
                        strs.contentToString() == "[a, null, b]" &&
                        ints.contentHashCode() == intsCopy.contentHashCode()
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `plus and plusElement for object and primitive arrays`() {
        assertBoxOk(
            """
            fun box(): String {
                val ints1 = intArrayOf(1, 2) + 3
                val ints2 = intArrayOf(1, 2) + intArrayOf(3, 4)

                val strs1 = arrayOf("a", "b") + "c"
                val strs2 = arrayOf("a").plusElement("b")
                val strs3 = arrayOf("a") + arrayOf("b", "c")

                val ok =
                    (ints1 contentEquals intArrayOf(1, 2, 3)) &&
                        (ints2 contentEquals intArrayOf(1, 2, 3, 4)) &&
                        (strs1 contentEquals arrayOf("a", "b", "c")) &&
                        (strs2 contentEquals arrayOf("a", "b")) &&
                        (strs3 contentEquals arrayOf("a", "b", "c"))
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `copyInto handles subranges self-overlap and null elements`() {
        assertBoxOk(
            """
            fun box(): String {
                val src = intArrayOf(10, 20, 30, 40, 50)
                val dst = intArrayOf(0, 0, 0, 0, 0)
                src.copyInto(dst, destinationOffset = 1, startIndex = 1, endIndex = 4)
                if (!(dst contentEquals intArrayOf(0, 20, 30, 40, 0))) return "fail basic"

                val shiftRight = intArrayOf(1, 2, 3, 4, 5)
                shiftRight.copyInto(shiftRight, destinationOffset = 2, startIndex = 0, endIndex = 3)
                if (!(shiftRight contentEquals intArrayOf(1, 2, 1, 2, 3))) return "fail right overlap"

                val shiftLeft = intArrayOf(1, 2, 3, 4, 5)
                shiftLeft.copyInto(shiftLeft, destinationOffset = 0, startIndex = 2, endIndex = 5)
                if (!(shiftLeft contentEquals intArrayOf(3, 4, 5, 4, 5))) return "fail left overlap"

                val nullsSrc = arrayOf<String?>("a", null, "c")
                val nullsDst = arrayOf<String?>("x", "y", "z")
                nullsSrc.copyInto(nullsDst)
                if (!(nullsDst contentEquals arrayOf<String?>("a", null, "c"))) return "fail nulls"

                var caughtBounds = false
                try {
                    src.copyInto(dst, destinationOffset = 4, startIndex = 0, endIndex = 3)
                } catch (_: IndexOutOfBoundsException) {
                    caughtBounds = true
                }
                if (!caughtBounds) return "fail bounds"

                var caughtOrder = false
                try {
                    src.copyInto(dst, destinationOffset = 0, startIndex = 3, endIndex = 1)
                } catch (_: IllegalArgumentException) {
                    caughtOrder = true
                }
                if (!caughtOrder) return "fail order"

                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `copyOf copyOfRange fill and array conversions`() {
        assertBoxOk(
            """
            fun box(): String {
                val orig = intArrayOf(1, 2, 3, 4)
                val copy = orig.copyOf()
                copy[0] = 99
                if (orig[0] != 1) return "fail copy independence"

                val grown = orig.copyOf(6)
                val shrunk = orig.copyOf(2)
                val range = orig.copyOfRange(1, 3)

                val objOrig = arrayOf("a", "b", "c")
                val objGrown = objOrig.copyOf(5)
                val objRange = objOrig.copyOfRange(1, 3)

                val filled = intArrayOf(1, 2, 3, 4, 5)
                filled.fill(9, fromIndex = 1, toIndex = 4)

                val objFilled = arrayOf<String?>("a", "b", "c")
                objFilled.fill(null, fromIndex = 1, toIndex = 2)

                val typed = orig.toTypedArray()
                val backToPrim = typed.toIntArray()

                val ok =
                    (grown contentEquals intArrayOf(1, 2, 3, 4, 0, 0)) &&
                        (shrunk contentEquals intArrayOf(1, 2)) &&
                        (range contentEquals intArrayOf(2, 3)) &&
                        (objGrown contentEquals arrayOf<String?>("a", "b", "c", null, null)) &&
                        (objRange contentEquals arrayOf("b", "c")) &&
                        (filled contentEquals intArrayOf(1, 9, 9, 9, 5)) &&
                        (objFilled contentEquals arrayOf<String?>("a", null, "c")) &&
                        (typed contentEquals arrayOf(1, 2, 3, 4)) &&
                        (backToPrim contentEquals orig)
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `primitive and comparable array sort including ranges and NaN`() {
        assertBoxOk(
            """
            fun box(): String {
                val ints = intArrayOf(5, 1, 4, 2, 3)
                ints.sort()

                val subInts = intArrayOf(9, 5, 2, 8, 1, 7)
                subInts.sort(fromIndex = 1, toIndex = 5)

                val chars = charArrayOf('d', 'a', 'c', 'b')
                chars.sort()

                val nan = 0.0 / 0.0
                val doubles = doubleArrayOf(nan, 2.0, -1.0, nan, 0.0)
                doubles.sort()

                val strs = arrayOf("pear", "apple", "orange", "banana")
                strs.sort()

                val ok =
                    (ints contentEquals intArrayOf(1, 2, 3, 4, 5)) &&
                        (subInts contentEquals intArrayOf(9, 1, 2, 5, 8, 7)) &&
                        (chars contentEquals charArrayOf('a', 'b', 'c', 'd')) &&
                        doubles[0] == -1.0 &&
                        doubles[1] == 0.0 &&
                        doubles[2] == 2.0 &&
                        doubles[3] != doubles[3] &&
                        doubles[4] != doubles[4] &&
                        (strs contentEquals arrayOf("apple", "banana", "orange", "pear"))
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
    fun `doubleArray sort orders negative zero before positive zero`() {
        assertBoxOk(
            """
            fun makeNegZero(): Double = -0.0
            fun makePosZero(): Double = 0.0

            fun box(): String {
                val pos = makePosZero()
                val neg = makeNegZero()
                val arr = doubleArrayOf(pos, neg)
                arr.sort()
                val ok = (arr contentEquals doubleArrayOf(neg, pos))
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `sortWith using fun interface Comparator is stable and supports subranges and nulls`() {
        assertBoxOk(
            """
            data class Item(val key: Int, val tag: String)

            fun box(): String {
                val items = arrayOf(
                    Item(3, "a"),
                    Item(1, "b"),
                    Item(3, "c"),
                    Item(2, "d"),
                    Item(1, "e"),
                    Item(3, "f"),
                )
                val byKey = Comparator<Item> { a, b -> a.key.compareTo(b.key) }
                items.sortWith(byKey)

                val expectedFull = arrayOf(
                    Item(1, "b"),
                    Item(1, "e"),
                    Item(2, "d"),
                    Item(3, "a"),
                    Item(3, "c"),
                    Item(3, "f"),
                )
                if (!(items contentEquals expectedFull)) return "fail stability"

                val sub = arrayOf("keep0", "ccc", "a", "bb", "keep4")
                val byLenDesc = Comparator<String> { a, b -> b.length.compareTo(a.length) }
                sub.sortWith(byLenDesc, fromIndex = 1, toIndex = 4)
                if (!(sub contentEquals arrayOf("keep0", "ccc", "bb", "a", "keep4"))) return "fail subrange"

                val nullable = arrayOf<String?>("b", null, "a", null)
                val nullsLast = Comparator<String?> { a, b ->
                    when {
                        a === b -> 0
                        a == null -> 1
                        b == null -> -1
                        else -> a.compareTo(b)
                    }
                }
                nullable.sortWith(nullsLast)
                if (!(nullable contentEquals arrayOf<String?>("a", "b", null, null))) return "fail nulls"

                return "OK"
            }
            """.trimIndent(),
        )
    }
}
