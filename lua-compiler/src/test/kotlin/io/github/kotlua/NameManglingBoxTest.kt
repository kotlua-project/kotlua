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

class NameManglingBoxTest : AbstractKotluaTest() {
    @Test
    fun `overloaded functions do not overwrite each other`() {
        assertBoxOk(
            """
            fun pick(x: Int): String = "int"
            fun pick(x: String): String = "str"

            fun box(): String {
                val res = pick(1) + pick("a")
                return if (res == "intstr") "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `same function names in different packages do not collide`() {
        assertBoxOk(
            mapOf(
                "pkg1/One.kt" to
                    """
                    package pkg1
                    fun tag(): String = "O"
                    """.trimIndent(),
                "pkg2/Two.kt" to
                    """
                    package pkg2
                    fun tag(): String = "K"
                    """.trimIndent(),
                "Main.kt" to
                    """
                    fun box(): String = pkg1.tag() + pkg2.tag()
                    """.trimIndent(),
            ),
        )
    }
}
