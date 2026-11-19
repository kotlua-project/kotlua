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

class OverrideAndSuperBoxTest : AbstractKotluaTest() {
    @Test
    fun `override val and var with polymorphic access and super property`() {
        assertBoxOk(
            """
            open class Base {
                open val label: String = "O"
                open var count: Int = 10
                fun readLabelPolymorphic(): String = label
            }

            class Derived : Base() {
                override val label: String = super.label + "K"
                override var count: Int = 20
            }

            fun box(): String {
                val instance: Base = Derived()
                val initialOk = instance.readLabelPolymorphic() == "OK" && instance.count == 20
                instance.count = 42
                return if (initialOk && instance.count == 42) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `override one overloaded method and call super for that overload`() {
        assertBoxOk(
            """
            open class Base {
                open fun compute(x: Int): Int = x + 10
                open fun compute(x: String): String = x + "!"
            }

            class Derived : Base() {
                override fun compute(x: Int): Int = super.compute(x) * 2
            }

            fun box(): String {
                val instance: Base = Derived()
                val ok = instance.compute(11) == 42 && instance.compute("O") == "O!"
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `three-level inheritance chain with super calls`() {
        assertBoxOk(
            """
            open class GrandParent {
                open fun value(): Int = 10
            }

            open class Parent : GrandParent() {
                override fun value(): Int = super.value() + 12
            }

            class Child : Parent() {
                override fun value(): Int = super.value() + 20
            }

            fun box(): String {
                val instance: GrandParent = Child()
                return if (instance.value() == 42) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `qualified super calls for multiple interfaces`() {
        assertBoxOk(
            """
            interface Left {
                fun side(): String = "O"
            }

            interface Right {
                fun side(): String = "K"
            }

            class Both : Left, Right {
                override fun side(): String = super<Left>.side() + super<Right>.side()
            }

            fun box(): String = Both().side()
            """.trimIndent(),
        )
    }
}
