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
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

class EqualitySemanticsBoxTest : AbstractKotluaTest() {
    @Test
    fun `nan equality differs between Double and Any`() {
        assertBoxOk(
            """
            fun box(): String {
                val nan1 = 0.0 / 0.0
                val nan2 = 0.0 / 0.0
                val a: Any = nan1
                val b: Any = nan2
                val ok = !(nan1 == nan2) && (nan1 != nan2) && (a == b) && !(a != b)
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
    fun `negative zero equals positive zero as Double but not via Any equals`() {
        assertBoxOk(
            """
            fun makeNegZero(): Double = -0.0
            fun makePosZero(): Double = 0.0

            fun box(): String {
                val negZero = makeNegZero()
                val posZero = makePosZero()
                val a: Any = negZero
                val b: Any = posZero
                val ok = (negZero == posZero) && (a != b)
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nullable Any equality with null`() {
        assertBoxOk(
            """
            fun box(): String {
                val a: Any? = null
                val b: Any? = null
                val c: Any? = 1
                val ok = (a == b) && !(a == c) && !(c == a) && (a != c)
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    @Disabled("TODO: Classes, is-check & polymorphic Any.equals dispatch in coreRuntime.equals")
    fun `structural equals vs referential equality for classes`() {
        assertBoxOk(
            """
            class Item(val id: Int) {
                override fun equals(other: Any?): Boolean = other is Item && other.id == id
            }

            fun box(): String {
                val a = Item(1)
                val b = Item(1)
                val c = Item(2)
                val anyA: Any? = a
                val anyB: Any? = b

                val ok =
                    (a == b) &&
                        (a != c) &&
                        !(a === b) &&
                        (a === a) &&
                        (anyA == anyB) &&
                        !(anyA === anyB)
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }
}
