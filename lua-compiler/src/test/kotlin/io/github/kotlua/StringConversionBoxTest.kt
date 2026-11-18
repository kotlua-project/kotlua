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

class StringConversionBoxTest : AbstractKotluaTest() {
    @Test
    fun `null converts to null rather than nil in string templates and concatenation`() {
        assertBoxOk(
            $$"""
            fun box(): String {
                val empty: String? = null
                val ok = "$empty" == "null" && ("v=" + empty) == "v=null"
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `booleans numbers and null concatenate without lua type errors`() {
        assertBoxOk(
            $$"""
            fun box(): String {
                val flag = true
                val off = false
                val count = 42
                val n: String? = null
                val s1 = "$flag-$off-$count"
                val s2 = "" + 1 + true + n
                return if (s1 == "true-false-42" && s2 == "1truenull") "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `direct toString calls on nullable Any and boxed primitives`() {
        assertBoxOk(
            """
            fun box(): String {
                val n: Any? = null
                val i: Any = 42
                val b: Any = true
                val ok = n.toString() == "null" && i.toString() == "42" && b.toString() == "true"
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    @Disabled("TODO: Classes & custom toString dispatch via coreRuntime.toString")
    fun `custom class toString used in concatenation and via Any`() {
        assertBoxOk(
            $$"""
            class Tag(val name: String) {
                override fun toString(): String = "Tag($name)"
            }

            fun box(): String {
                val tag = Tag("ok")
                val asAny: Any = tag
                val ok = ("item=" + tag) == "item=Tag(ok)" && "$asAny" == "Tag(ok)"
                return if (ok) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }
}
