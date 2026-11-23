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

class ArrayListBoxTest : AbstractKotluaTest() {
    @Test
    fun `nullable elements preserve size indices contains get and set`() {
        assertBoxOk(
            """
            fun box(): String {
                val onlyNulls = ArrayList<Int?>()
                onlyNulls.add(null)
                onlyNulls.add(null)
                onlyNulls.add(null)
                if (onlyNulls.size != 3 || onlyNulls[0] != null || onlyNulls[2] != null) return "fail onlyNulls"

                val list = ArrayList<String?>()
                list.add(null)
                list.add("a")
                list.add(null)
                list.add("b")
                list.add(null)

                if (list.size != 5) return "fail size"
                if (list[0] != null || list[1] != "a" || list[2] != null || list[3] != "b" || list[4] != null) {
                    return "fail get"
                }
                if (!list.contains(null) || !list.contains("a") || list.contains("missing")) return "fail contains"

                val prev0 = list.set(0, "head")
                val prev1 = list.set(1, null)
                val ok =
                    prev0 == null &&
                        prev1 == "a" &&
                        list[0] == "head" &&
                        list[1] == null &&
                        list.size == 5
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `indexOf lastIndexOf remove and removeAt with nullable elements`() {
        assertBoxOk(
            """
            fun box(): String {
                val list = ArrayList<String?>()
                list.add("a")
                list.add(null)
                list.add("b")
                list.add(null)
                list.add("c")

                if (list.indexOf(null) != 1) return "fail indexOf null"
                if (list.lastIndexOf(null) != 3) return "fail lastIndexOf null"
                if (list.indexOf("missing") != -1) return "fail indexOf missing"

                if (!list.remove(null)) return "fail remove first null"
                // ["a", "b", null, "c"]
                if (list.size != 4 || list[1] != "b" || list[2] != null || list[3] != "c") return "fail after remove null"

                val removed = list.removeAt(2)
                if (removed != null || list.size != 3 || list[2] != "c") return "fail removeAt null"

                if (list.remove(null)) return "fail remove nonexistent null"

                val popped = list.removeAt(list.lastIndex)
                if (popped != "c" || list.size != 2 || list[0] != "a" || list[1] != "b") return "fail pop"

                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `indexed add addAll and self addAll with null holes`() {
        assertBoxOk(
            """
            fun box(): String {
                val list = ArrayList<String?>()
                list.add(0, null)
                list.add(1, "end")
                list.add(1, "mid")
                list.add(2, null)
                // [null, "mid", null, "end"]
                if (list.size != 4 || list[0] != null || list[1] != "mid" || list[2] != null || list[3] != "end") {
                    return "fail indexed add"
                }

                val extra = ArrayList<String?>()
                extra.add("x")
                extra.add(null)
                list.addAll(2, extra)
                // [null, "mid", "x", null, null, "end"]
                if (list.size != 6 || list[2] != "x" || list[3] != null || list[4] != null || list[5] != "end") {
                    return "fail indexed addAll"
                }

                list.addAll(list)
                if (list.size != 12) return "fail self addAll size"
                if (list[6] != null || list[7] != "mid" || list[8] != "x" || list[9] != null || list[11] != "end") {
                    return "fail self addAll elements"
                }

                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `iteration subList clear and toTypedArray with null elements`() {
        assertBoxOk(
            """
            fun box(): String {
                val list = ArrayList<Int?>()
                list.add(1)
                list.add(null)
                list.add(2)
                list.add(null)
                list.add(3)

                var count = 0
                var nullCount = 0
                var sum = 0
                for (item in list) {
                    count++
                    if (item == null) {
                        nullCount++
                    } else {
                        sum += item
                    }
                }
                if (count != 5 || nullCount != 2 || sum != 6) return "fail iteration"

                val arr = list.toTypedArray()
                if (arr.size != 5 || arr[1] != null || arr[3] != null || arr[4] != 3) return "fail toTypedArray"

                val sub = list.subList(1, 4)
                if (sub.size != 3 || sub[0] != null || sub[1] != 2 || sub[2] != null) return "fail subList"
                sub.clear()
                if (list.size != 2 || list[0] != 1 || list[1] != 3) return "fail subList clear"

                list.clear()
                if (!list.isEmpty() || list.size != 0) return "fail clear"

                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `toString equals hashCode copy constructor and buildList readOnly`() {
        assertBoxOk(
            """
            fun box(): String {
                val a = ArrayList<Any?>()
                a.add("x")
                a.add(null)
                a.add(a)
                if (a.toString() != "[x, null, (this Collection)]") return "fail self toString"

                val l1 = ArrayList<Int?>()
                l1.add(10)
                l1.add(null)
                l1.add(20)

                val l2 = ArrayList(l1)
                val l3 = ArrayList<Int?>()
                l3.add(10)
                l3.add(null)
                l3.add(30)

                if (l1 != l2 || l1.hashCode() != l2.hashCode() || l1 == l3) return "fail equals hashCode"
                if (l1.toString() != "[10, null, 20]") return "fail toString"

                val built = buildList<String?> {
                    add("first")
                    add(null)
                    add("last")
                }
                if (built.size != 3 || built[1] != null) return "fail buildList"

                var readOnlyBlocked = false
                try {
                    @Suppress("UNCHECKED_CAST")
                    (built as MutableList<String?>).add("extra")
                } catch (_: UnsupportedOperationException) {
                    readOnlyBlocked = true
                }
                if (!readOnlyBlocked) return "fail readOnly"

                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `fill sortWith and bounds checks with nullable elements`() {
        assertBoxOk(
            """
            fun box(): String {
                val list = ArrayList<String?>()
                list.add("b")
                list.add(null)
                list.add("a")
                list.add(null)

                list.sortWith(
                    Comparator { x, y ->
                        when {
                            x === y -> 0
                            x == null -> 1
                            y == null -> -1
                            else -> x.compareTo(y)
                        }
                    },
                )
                if (list.toString() != "[a, b, null, null]") return "fail sortWith"

                list.fill(null)
                if (list.size != 4 || list[0] != null || list[3] != null) return "fail fill null"

                var negCapCaught = false
                try {
                    ArrayList<Int>(-1)
                } catch (_: IllegalArgumentException) {
                    negCapCaught = true
                }
                if (!negCapCaught) return "fail neg capacity"

                var oobCaught = false
                try {
                    list[4]
                } catch (_: IndexOutOfBoundsException) {
                    oobCaught = true
                }
                if (!oobCaught) return "fail oob get"

                var addOobCaught = false
                try {
                    list.add(5, "z")
                } catch (_: IndexOutOfBoundsException) {
                    addOobCaught = true
                }
                if (!addOobCaught) return "fail oob add"

                return "OK"
            }
            """.trimIndent(),
        )
    }
}
