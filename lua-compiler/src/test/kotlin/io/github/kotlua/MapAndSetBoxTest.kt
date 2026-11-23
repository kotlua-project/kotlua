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

class MapAndSetBoxTest : AbstractKotluaTest() {
    @Test
    fun `HashMap basic CRUD null keys null values and rehashing`() =
        assertBoxOk(
            """
            fun box(): String {
                val map = HashMap<String?, Int?>()
                if (!map.isEmpty() || map.size != 0) return "FAIL: initial"

                if (map.put("a", 1) != null) return "FAIL: put a"
                if (map.put("b", null) != null) return "FAIL: put b"
                if (map.put(null, 42) != null) return "FAIL: put null"
                if (map.size != 3 || map.isEmpty()) return "FAIL: size 3"

                if (map["a"] != 1 || map["b"] != null || map[null] != 42) return "FAIL: get"
                if (!map.containsKey("b") || !map.containsKey(null) || map.containsKey("missing")) {
                    return "FAIL: containsKey"
                }
                if (!map.containsValue(null) || !map.containsValue(42) || map.containsValue(99)) {
                    return "FAIL: containsValue"
                }

                if (map.put("a", 10) != 1 || map["a"] != 10 || map.size != 3) return "FAIL: overwrite"
                if (map.put(null, null) != 42 || map[null] != null || !map.containsKey(null)) {
                    return "FAIL: overwrite null"
                }

                if (map.remove("b") != null || map.containsKey("b") || map.size != 2) return "FAIL: remove b"
                if (map.remove(null) != null || map.containsKey(null) || map.size != 1) return "FAIL: remove null"
                if (map.remove("missing") != null) return "FAIL: remove missing"

                val big = HashMap<Int, String>(2, 0.75f)
                for (i in 0 until 64) {
                    big[i] = "v" + i
                }
                if (big.size != 64) return "FAIL: big size=" + big.size
                for (i in 0 until 64) {
                    if (big[i] != "v" + i) return "FAIL: big get " + i
                }
                for (i in 0 until 32) {
                    if (big.remove(i * 2) != "v" + (i * 2)) return "FAIL: big remove " + i
                }
                if (big.size != 32) return "FAIL: big after remove"
                for (i in 0 until 64) {
                    val expected = if (i % 2 == 0) null else "v" + i
                    if (big[i] != expected || big.containsKey(i) != (i % 2 != 0)) {
                        return "FAIL: big verify " + i
                    }
                }

                big.clear()
                if (!big.isEmpty() || big.size != 0 || big.containsKey(1)) return "FAIL: clear"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `HashMap custom equals hashCode collisions and NaN keys`() =
        assertBoxOk(
            """
            class CollidingKey(val group: Int, val id: String) {
                override fun hashCode(): Int = group
                override fun equals(other: Any?): Boolean =
                    other is CollidingKey && group == other.group && id == other.id
            }

            fun box(): String {
                val map = HashMap<Any?, String>()
                val k1 = CollidingKey(7, "one")
                val k2 = CollidingKey(7, "two")
                val k3 = CollidingKey(7, "three")

                map[k1] = "v1"
                map[k2] = "v2"
                map[k3] = "v3"
                if (map.size != 3) return "FAIL: collision size"

                if (map[CollidingKey(7, "one")] != "v1") return "FAIL: lookup k1"
                if (map[CollidingKey(7, "two")] != "v2") return "FAIL: lookup k2"
                if (map[CollidingKey(7, "three")] != "v3") return "FAIL: lookup k3"
                if (map[CollidingKey(7, "four")] != null) return "FAIL: lookup missing"

                if (map.remove(CollidingKey(7, "two")) != "v2" || map.size != 2) return "FAIL: remove middle"
                if (map[CollidingKey(7, "one")] != "v1" || map[CollidingKey(7, "three")] != "v3") {
                    return "FAIL: remaining after collision remove"
                }

                val nanMap = HashMap<Double, String>()
                nanMap[Double.NaN] = "nan"
                if (nanMap[0.0 / 0.0] != "nan" || !nanMap.containsKey(Double.NaN)) return "FAIL: NaN key"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    @EnabledIfSystemProperty(named = "kotlua.test.luaExecutable", matches = ".+")
    fun `HashMap and HashSet distinguish negative zero from positive zero`() =
        assertBoxOk(
            """
            fun negativeZero() = -0.0
            fun positiveZero() = 0.0
            fun box(): String {
                val map = HashMap<Double, String>()
                map[positiveZero()] = "pos"
                map[negativeZero()] = "neg"
                if (map.size != 2) return "FAIL: map size=" + map.size
                if (map[positiveZero()] != "pos" || map[negativeZero()] != "neg") return "FAIL: map values"

                val set = HashSet<Double>()
                if (!set.add(positiveZero()) || !set.add(negativeZero()) || set.size != 2) return "FAIL: set size=" + set.size
                if (!set.contains(positiveZero()) || !set.contains(negativeZero())) return "FAIL: set contains"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `LinkedHashMap preserves insertion order on updates and removals`() =
        assertBoxOk(
            """
            fun joinKeys(map: Map<String, Int>): String {
                val sb = StringBuilder()
                for (entry in map.entries) {
                    if (sb.length > 0) sb.append(',')
                    sb.append(entry.key).append(':').append(entry.value)
                }
                return sb.toString()
            }

            fun box(): String {
                val map = LinkedHashMap<String, Int>()
                map["a"] = 1
                map["b"] = 2
                map["c"] = 3
                map["d"] = 4
                if (joinKeys(map) != "a:1,b:2,c:3,d:4") return "FAIL: initial=" + joinKeys(map)

                // Updating an existing key must not move it.
                map["b"] = 20
                if (joinKeys(map) != "a:1,b:20,c:3,d:4") return "FAIL: update=" + joinKeys(map)

                map.remove("a")
                map.remove("c")
                map["a"] = 100
                if (joinKeys(map) != "b:20,d:4,a:100") return "FAIL: remove+reinsert=" + joinKeys(map)

                val copy = LinkedHashMap(map)
                if (joinKeys(copy) != "b:20,d:4,a:100") return "FAIL: copy=" + joinKeys(copy)

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `HashMap entries keys values views and iterator remove`() =
        assertBoxOk(
            """
            fun box(): String {
                val map = LinkedHashMap<String, Int>()
                map["k1"] = 10
                map["k2"] = 20
                map["k3"] = 30

                val entryIter = map.entries.iterator()
                val firstEntry = entryIter.next()
                if (firstEntry.key != "k1" || firstEntry.value != 10) return "FAIL: firstEntry"
                if (firstEntry.setValue(15) != 10 || map["k1"] != 15) return "FAIL: setValue"
                entryIter.remove()
                if (map.size != 2 || map.containsKey("k1")) return "FAIL: entryIter.remove"

                val keyIter = map.keys.iterator()
                if (keyIter.next() != "k2") return "FAIL: keyIter"
                keyIter.remove()
                if (map.size != 1 || map.containsKey("k2")) return "FAIL: keyIter.remove"

                if (!map.values.contains(30) || !map.values.remove(30) || !map.isEmpty()) {
                    return "FAIL: values.remove"
                }

                map["x"] = 1
                map["y"] = 2
                if (!map.keys.remove("x") || map.keys.remove("missing") || map.size != 1) {
                    return "FAIL: keys.remove"
                }

                val e = map.entries.iterator().next()
                if (!map.entries.contains(e) || !map.entries.remove(e) || !map.isEmpty()) {
                    return "FAIL: entries.remove"
                }

                map["z"] = 9
                map.keys.clear()
                if (!map.isEmpty()) return "FAIL: keys.clear"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `HashSet and LinkedHashSet operations and set equality`() =
        assertBoxOk(
            """
            fun joinSet(set: Set<String?>): String {
                val sb = StringBuilder()
                for (e in set) {
                    if (sb.length > 0) sb.append(',')
                    sb.append(e)
                }
                return sb.toString()
            }

            fun box(): String {
                val set = LinkedHashSet<String?>()
                if (!set.add("b") || !set.add(null) || !set.add("a")) return "FAIL: add"
                if (set.add("b") || set.add(null)) return "FAIL: duplicate add"
                if (set.size != 3 || !set.contains("a") || !set.contains(null)) return "FAIL: contains"
                if (joinSet(set) != "b,null,a") return "FAIL: order=" + joinSet(set)

                if (!set.remove(null) || set.remove(null) || set.size != 2) return "FAIL: remove null"

                val other = HashSet<String?>(listOf("a", "b", "a"))
                if (set != other || set.hashCode() != other.hashCode()) return "FAIL: set equals/hashCode"

                val iter = set.iterator()
                val first = iter.next()
                iter.remove()
                if (set.size != 1 || set.contains(first)) return "FAIL: iterator.remove"

                set.addAll(listOf("c", "d"))
                set.retainAll(listOf("a", "c"))
                if (set.size != 2 || !set.contains("a") || !set.contains("c")) return "FAIL: retainAll"

                set.removeAll(listOf("a"))
                if (set.size != 1 || !set.contains("c")) return "FAIL: removeAll"

                set.clear()
                if (!set.isEmpty()) return "FAIL: clear"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `mapOf setOf buildMap buildSet and read-only enforcement`() =
        assertBoxOk(
            """
            fun box(): String {
                val emptyM = mapOf<String, Int>()
                if (!emptyM.isEmpty() || emptyM.size != 0) return "FAIL: emptyMap"

                val singleM = mapOf("one" to 1)
                if (singleM.size != 1 || singleM["one"] != 1) return "FAIL: single mapOf"

                val multiM = mapOf("a" to 1, "b" to 2, "c" to 3)
                if (multiM.size != 3 || multiM["b"] != 2) return "FAIL: multi mapOf"

                val builtM = buildMap<String, Int> {
                    put("x", 10)
                    put("y", 20)
                }
                if (builtM.size != 2 || builtM["x"] != 10 || builtM["y"] != 20) return "FAIL: buildMap"

                val builtEmptyM = buildMap<String, Int>(4) {}
                if (!builtEmptyM.isEmpty()) return "FAIL: buildMap empty"

                val emptyS = setOf<String>()
                if (!emptyS.isEmpty()) return "FAIL: emptySet"

                val singleS = setOf("only")
                if (singleS.size != 1 || !singleS.contains("only")) return "FAIL: single setOf"

                val multiS = setOf("a", "b", "a", "c")
                if (multiS.size != 3 || !multiS.contains("c")) return "FAIL: multi setOf"

                val builtS = buildSet<Int>(4) {
                    add(1)
                    add(2)
                    add(1)
                }
                if (builtS.size != 2 || !builtS.contains(1) || !builtS.contains(2)) return "FAIL: buildSet"

                try {
                    (singleM as MutableMap<String, Int>)["two"] = 2
                    return "FAIL: singleM mutable"
                } catch (_: UnsupportedOperationException) {
                }

                try {
                    (builtM as MutableMap<String, Int>).keys.remove("x")
                    return "FAIL: builtM keys mutable"
                } catch (_: UnsupportedOperationException) {
                }

                try {
                    (builtS as MutableSet<Int>).add(3)
                    return "FAIL: builtS mutable"
                } catch (_: UnsupportedOperationException) {
                }
                
                val builtEmptyS = buildSet<Int>(4) {}
                try {
                    (builtEmptyS as MutableSet<Int>).add(1)
                    return "FAIL: builtEmptyS mutable"
                } catch (_: UnsupportedOperationException) {
                }

                try {
                    (emptyS as MutableSet<String>).clear()
                    return "FAIL: emptyS mutable"
                } catch (_: ClassCastException) {
                }

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `Grouping eachCount and singleton map conversion`() =
        assertBoxOk(
            """
            fun box(): String {
                val items = listOf("a", "bb", "c", "ddd", "ee", "f")
                val grouping = object : Grouping<String, Int> {
                    override fun sourceIterator(): Iterator<String> = items.iterator()
                    override fun keyOf(element: String): Int = element.length
                }
                val counts = grouping.eachCount()
                if (counts.size != 3 || counts[1] != 3 || counts[2] != 2 || counts[3] != 1) {
                    return "FAIL: eachCount=" + counts.toString()
                }

                val mutable = mutableMapOf("only" to 99)
                val immutable = mutable.toMap()
                mutable["other"] = 100
                if (immutable.size != 1 || immutable["only"] != 99 || immutable.containsKey("other")) {
                    return "FAIL: toMap singleton"
                }

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `Map and Set equals hashCode and toString`() =
        assertBoxOk(
            """
            fun box(): String {
                val m1 = linkedMapOf("a" to 1, "b" to 2)
                val m2 = hashMapOf("b" to 2, "a" to 1)
                if (m1 != m2 || m1.hashCode() != m2.hashCode()) return "FAIL: map equals/hashCode"
                if (m1.toString() != "{a=1, b=2}") return "FAIL: m1.toString=" + m1.toString()
                if (emptyMap<String, Int>().toString() != "{}") return "FAIL: emptyMap.toString"

                val selfMap = LinkedHashMap<String, Any?>()
                selfMap["self"] = selfMap
                if (selfMap.toString() != "{self=(this Map)}") return "FAIL: selfMap=" + selfMap.toString()

                val s1 = linkedSetOf("x", "y")
                val s2 = hashSetOf("y", "x")
                if (s1 != s2 || s1.hashCode() != s2.hashCode()) return "FAIL: set equals/hashCode"
                if (s1.toString() != "[x, y]") return "FAIL: s1.toString=" + s1.toString()

                val selfSet = LinkedHashSet<Any?>()
                selfSet.add(selfSet)
                if (selfSet.toString() != "[(this Collection)]") return "FAIL: selfSet=" + selfSet.toString()

                return "OK"
            }
            """.trimIndent(),
        )
}
