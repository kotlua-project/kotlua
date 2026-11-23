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

class BridgeInheritanceBoxTest : AbstractKotluaTest() {
    @Test
    fun `subclass inherits generic superclass implementation for UnsafeVariance interface`() =
        assertBoxOk(
            """
            open class BaseBag<E>(private val items: List<E>) {
                open fun hasItem(item: E): Boolean {
                    for (e in items) {
                        if (e == item) return true
                    }
                    return false
                }

                open fun echoItem(item: E): E = item
            }

            interface ReadBag<out E> {
                fun hasItem(item: @UnsafeVariance E): Boolean
                fun echoItem(item: @UnsafeVariance E): E
            }

            class SubBag<E>(items: List<E>) : BaseBag<E>(items), ReadBag<E>

            fun box(): String {
                val bag: ReadBag<String> = SubBag(listOf("alpha", "beta"))
                if (!bag.hasItem("alpha") || bag.hasItem("gamma")) return "FAIL: hasItem"
                if (bag.echoItem("delta") != "delta") return "FAIL: echoItem"
                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `concrete subclass inherits parameterized superclass method for non-generic interface`() =
        assertBoxOk(
            """
            open class GenericBox<T>(private var current: T) {
                open fun exchange(next: T): T {
                    val prev = current
                    current = next
                    return prev
                }

                open fun matches(candidate: T): Boolean = current == candidate
            }

            interface StringBox {
                fun exchange(next: String): String
                fun matches(candidate: String): Boolean
            }

            class ConcreteStringBox(initial: String) : GenericBox<String>(initial), StringBox

            fun box(): String {
                val box: StringBox = ConcreteStringBox("first")
                if (!box.matches("first")) return "FAIL: initial match"
                val prev = box.exchange("second")
                if (prev != "first") return "FAIL: prev=" + prev
                if (!box.matches("second") || box.matches("first")) return "FAIL: updated match"
                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `multi-level inheritance bridges superclass implementation through abstract intermediate class`() =
        assertBoxOk(
            """
            open class TopRepo<T>(private val value: T) {
                open fun fetch(): T = value
                open fun sameAs(other: T): Boolean = value == other
            }

            abstract class MiddleRepo<E>(value: E) : TopRepo<E>(value)

            interface ReadRepo<out E> {
                fun fetch(): E
                fun sameAs(other: @UnsafeVariance E): Boolean
            }

            class LeafRepo<E>(value: E) : MiddleRepo<E>(value), ReadRepo<E>

            fun box(): String {
                val repo: ReadRepo<Int> = LeafRepo(42)
                if (repo.fetch() != 42) return "FAIL: fetch"
                if (!repo.sameAs(42) || repo.sameAs(99)) return "FAIL: sameAs"
                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `generic superclass method returning T bridges to Unit-returning interface method`() =
        assertBoxOk(
            """
            open class GenericAction<T>(private val onRun: (T) -> Unit) {
                open fun execute(arg: T): T {
                    onRun(arg)
                    return arg
                }
            }

            interface UnitAction {
                fun execute(arg: Unit)
            }

            class ConcreteUnitAction(onRun: (Unit) -> Unit) : GenericAction<Unit>(onRun), UnitAction

            fun box(): String {
                var calls = 0
                val action: UnitAction = ConcreteUnitAction { calls++ }
                action.execute(Unit)
                action.execute(Unit)
                if (calls != 2) return "FAIL: calls=" + calls
                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `inherited method with function type parameters bridges to specialized interface`() =
        assertBoxOk(
            $$"""
            open class BaseTransformer<T> {
                open fun <R> transform(input: T, fn: (T) -> R): R = fn(input)
            }

            interface StringTransformer {
                fun <R> transform(input: String, fn: (String) -> R): R
            }

            class ConcreteTransformer : BaseTransformer<String>(), StringTransformer

            fun box(): String {
                val t: StringTransformer = ConcreteTransformer()
                val len = t.transform("kotlua") { it.length }
                val upper = t.transform("ok") { it + "!" }
                if (len != 6 || upper != "ok!") return "FAIL: len=$len upper=$upper"
                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `sub-subclass overriding bridged method dispatches polymorphically via both base and interface`() =
        assertBoxOk(
            """
            open class BaseCheck<E> {
                open fun accept(value: E): Boolean = false
            }

            interface CovariantCheck<out E> {
                fun accept(value: @UnsafeVariance E): Boolean
            }

            open class InheritedCheck<E> : BaseCheck<E>(), CovariantCheck<E>

            class AlwaysTrueStringCheck : InheritedCheck<String>() {
                override fun accept(value: String): Boolean = value == "yes"
            }

            fun box(): String {
                val impl = AlwaysTrueStringCheck()
                val asBase: BaseCheck<String> = impl
                val asIface: CovariantCheck<String> = impl
                val defaultInherited: CovariantCheck<String> = InheritedCheck()

                if (!asBase.accept("yes") || asBase.accept("no")) return "FAIL: asBase"
                if (!asIface.accept("yes") || asIface.accept("no")) return "FAIL: asIface"
                if (defaultInherited.accept("yes")) return "FAIL: defaultInherited"
                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `stdlib collection interfaces dispatch inherited superclass implementations`() =
        assertBoxOk(
            """
            fun box(): String {
                val set: Set<String> = linkedSetOf("a", "b", "c")
                if (!set.contains("b") || set.contains("z")) return "FAIL: Set.contains"
                if (!set.containsAll(listOf("a", "c")) || set.containsAll(listOf("a", "z"))) {
                    return "FAIL: Set.containsAll"
                }

                val list: List<Int> = arrayListOf(10, 20, 30)
                if (!list.contains(20) || list.contains(99)) return "FAIL: List.contains"
                if (!list.containsAll(listOf(10, 30)) || list.containsAll(listOf(10, 99))) {
                    return "FAIL: List.containsAll"
                }
                if (list.indexOf(20) != 1 || list.lastIndexOf(30) != 2) return "FAIL: List.indexOf"

                val map: Map<String, Int?> = linkedMapOf("x" to 1, "y" to null)
                if (map["x"] != 1 || !map.containsKey("y") || !map.containsValue(null)) {
                    return "FAIL: Map interface"
                }

                return "OK"
            }
            """.trimIndent(),
        )
}
