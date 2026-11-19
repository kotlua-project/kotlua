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

class ObjectsBoxTest : AbstractKotluaTest() {
    @Test
    fun `top-level object singleton semantics and lazy initialization`() {
        assertBoxOk(
            """
            private var initCount = 0

            interface Named {
                fun name(): String
            }

            open class BaseService(val prefix: String)

            object Registry : BaseService("reg:"), Named {
                var counter = 0

                init {
                    initCount++
                }

                override fun name(): String = prefix + "Registry"

                fun bump(): Int {
                    counter++
                    return counter
                }
            }

            fun box(): String {
                if (initCount != 0) return "FAIL: init before access"

                val r1 = Registry
                val r2 = Registry
                val sameRef = r1 === r2
                val isNamed = r1 is Named
                val isService = r1 is BaseService

                val b1 = Registry.bump()
                val b2 = r2.bump()
                val n = Registry.name()

                return if (sameRef && initCount == 1 && isNamed && isService &&
                    b1 == 1 && b2 == 2 && r1.counter == 2 && n == "reg:Registry"
                ) {
                    "OK"
                } else {
                    "FAIL:" + initCount + ":" + sameRef + ":" + r1.counter + ":" + n
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `companion object default and explicit names with interface implementation`() {
        assertBoxOk(
            """
            interface Factory<T> {
                fun create(seed: Int): T
            }

            class Item(val value: Int) {
                companion object : Factory<Item> {
                    const val DEFAULT_OFFSET = 10
                    var createdTotal = 0

                    override fun create(seed: Int): Item {
                        createdTotal++
                        return Item(seed + DEFAULT_OFFSET)
                    }

                    fun helper(): String = "ItemCompanion"
                }
            }

            class Container(val raw: String) {
                companion object NamedCompanion {
                    fun parse(s: String): Container = Container("parsed:" + s)
                }
            }

            fun box(): String {
                val it1 = Item.create(5)
                val it2 = Item.Companion.create(20)
                val h = Item.helper()

                val c1 = Container.parse("data")
                val c2 = Container.NamedCompanion.parse("other")

                val companionSame = Item.Companion === Item.Companion
                val isFactory = Item.Companion is Factory<*>

                return if (it1.value == 15 && it2.value == 30 && Item.createdTotal == 2 &&
                    h == "ItemCompanion" && c1.raw == "parsed:data" && c2.raw == "parsed:other" &&
                    companionSame && isFactory
                ) {
                    "OK"
                } else {
                    "FAIL"
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `anonymous object expression with inheritance and captured vars`() {
        assertBoxOk(
            """
            interface ClickListener {
                fun onClick(tag: String): String
            }

            open class ContextHolder(val ctxName: String)

            fun box(): String {
                var clicks = 0
                val tagPrefix = "evt:"

                fun makeListener(name: String): ClickListener {
                    return object : ContextHolder(name), ClickListener {
                        override fun onClick(tag: String): String {
                            clicks++
                            return ctxName + "/" + tagPrefix + tag + "=" + clicks
                        }
                    }
                }

                val l1 = makeListener("btn1")
                val l2 = makeListener("btn2")

                val r1 = l1.onClick("first")
                val r2 = l2.onClick("second")
                val r3 = l1.onClick("third")

                val isContext = l1 is ContextHolder
                val isListener = l1 is ClickListener

                return if (r1 == "btn1/evt:first=1" &&
                    r2 == "btn2/evt:second=2" &&
                    r3 == "btn1/evt:third=3" &&
                    clicks == 3 && isContext && isListener
                ) {
                    "OK"
                } else {
                    "FAIL:" + r1 + "|" + r2 + "|" + r3 + ":" + clicks
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `top-level object with state, methods, and mutation`() {
        assertBoxOk(
            """
            object Counter {
                var count = 0
                val initial = 10

                init {
                    count = initial
                }

                fun inc(delta: Int): Int {
                    count += delta
                    return count
                }
            }

            fun box(): String {
                val c1 = Counter.count
                val c2 = Counter.inc(5)
                val c3 = Counter.inc(2)
                val sameRef = Counter === Counter
                return if (c1 == 10 && c2 == 15 && c3 == 17 && sameRef) "OK" else "FAIL:" + c1 + ":" + c2 + ":" + c3
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `companion object with default and custom name`() {
        assertBoxOk(
            """
            class Host(val value: Int) {
                companion object {
                    val TAG = "HOST_TAG"
                    var created = 0

                    fun create(v: Int): Host {
                        created++
                        return Host(v)
                    }
                }
            }

            class NamedHost {
                companion object Factory {
                    fun id(): String = "NAMED_FACTORY"
                }
            }

            fun box(): String {
                val tag = Host.TAG
                val h1 = Host.create(10)
                val h2 = Host.Companion.create(20)
                val count = Host.created

                val nId = NamedHost.id()
                val nIdDirect = NamedHost.Factory.id()

                return if (tag == "HOST_TAG" && h1.value == 10 && h2.value == 20 &&
                    count == 2 && nId == "NAMED_FACTORY" && nIdDirect == "NAMED_FACTORY"
                ) {
                    "OK"
                } else {
                    "FAIL"
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `object extending class and implementing interfaces with is and as`() {
        assertBoxOk(
            """
            interface Greeter {
                fun greet(name: String): String
            }

            open class NamedService(val serviceName: String) {
                open fun info(): String = "Service:" + serviceName
            }

            object DefaultGreeter : NamedService("Default"), Greeter {
                override fun greet(name: String): String = "Hello, " + name
                override fun info(): String = super.info() + " [Greeter]"
            }

            fun box(): String {
                val g: Any = DefaultGreeter
                val isGreeter = g is Greeter
                val isNamed = g is NamedService
                val isNotString = g !is String

                val greeted = (g as Greeter).greet("Kotlua")
                val info = (g as NamedService).info()

                val safeCast = (g as? Greeter)?.greet("Safe")

                return if (isGreeter && isNamed && isNotString &&
                    greeted == "Hello, Kotlua" &&
                    info == "Service:Default [Greeter]" &&
                    safeCast == "Hello, Safe"
                ) {
                    "OK"
                } else {
                    "FAIL"
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `cyclically referenced objects resolve without infinite recursion`() {
        assertBoxOk(
            """
            object NodeA {
                val name = "A"
                val neighbor get() = NodeB
            }

            object NodeB {
                val name = "B"
                val neighbor get() = NodeA
            }

            fun box(): String {
                val aName = NodeA.name
                val bName = NodeA.neighbor.name
                val backToA = NodeA.neighbor.neighbor.name
                val sameA = NodeA === NodeA.neighbor.neighbor

                return if (aName == "A" && bName == "B" && backToA == "A" && sameA) {
                    "OK"
                } else {
                    "FAIL"
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `sealed hierarchy with object and data class in when expressions`() {
        assertBoxOk(
            """
            sealed interface Result
            object Loading : Result
            object Empty : Result
            data class Success(val code: Int) : Result
            data class Error(val message: String) : Result

            private fun describe(r: Result): String =
                when (r) {
                    is Loading -> "loading"
                    is Empty -> "empty"
                    is Success -> "success:" + r.code
                    is Error -> "error:" + r.message
                }

            fun box(): String {
                val s1 = describe(Loading)
                val s2 = describe(Empty)
                val s3 = describe(Success(200))
                val s4 = describe(Error("fail"))

                val okLoading = Loading is Result && Loading is Loading
                return if (s1 == "loading" && s2 == "empty" && s3 == "success:200" && s4 == "error:fail" && okLoading) {
                    "OK"
                } else {
                    "FAIL"
                }
            }
            """.trimIndent(),
        )
    }
}
