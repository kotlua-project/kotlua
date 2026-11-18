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

class InheritanceOrderBoxTest : AbstractKotluaTest() {
    @Test
    @Disabled("TODO: Classes, interfaces, metatables & topological sorting of supertypes")
    fun `subclass and interface implementations declared before their supertypes`() {
        assertBoxOk(
            """
            class Child : Parent(), Greeter {
                override fun greet(): String = baseWord() + "K"
            }

            interface Greeter {
                fun greet(): String
            }

            open class Parent {
                fun baseWord(): String = "O"
            }

            fun box(): String = Child().greet()
            """.trimIndent(),
        )
    }
}
