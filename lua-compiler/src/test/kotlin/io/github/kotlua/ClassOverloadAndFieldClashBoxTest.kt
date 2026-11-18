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

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class ClassOverloadAndFieldClashBoxTest : AbstractKotluaTest() {
    @Test
    @Disabled("TODO: Classes, member method overloads & LuaNameLinkingNamer")
    fun `overloaded methods in base and subclass`() {
        assertBoxOk(
            """
            open class Base {
                fun tag(x: Int): String = "B_int"
                fun tag(x: String): String = "B_str"
            }

            class Sub : Base() {
                fun tag(x: Boolean): String = "S_bool"
            }

            fun box(): String {
                val base = Base()
                val sub = Sub()
                val ok =
                    base.tag(1) == "B_int" &&
                        sub.tag(1) == "B_int" &&
                        sub.tag("a") == "B_str" &&
                        sub.tag(true) == "S_bool"
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    @Disabled("TODO: Classes & separate naming/storage for fields and methods with the same name")
    fun `field and method with same name in same class`() {
        assertBoxOk(
            """
            class Holder {
                val data: Int = 10
                fun data(): Int = 32
            }

            fun box(): String {
                val holder = Holder()
                return if (holder.data + holder.data() == 42) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    @Disabled("TODO: Classes & private field mangling across inheritance hierarchy")
    fun `private field in base class does not collide with field of same name in subclass`() {
        assertBoxOk(
            """
            open class Base {
                private val secret: Int = 10
                fun readBaseSecret(): Int = secret
            }

            class Derived : Base() {
                val secret: Int = 32
            }

            fun box(): String {
                val derived = Derived()
                return if (derived.readBaseSecret() + derived.secret == 42) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }
}
