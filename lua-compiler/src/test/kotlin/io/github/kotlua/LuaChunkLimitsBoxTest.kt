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

class LuaChunkLimitsBoxTest : AbstractKotluaTest() {
    @Test
    fun `more than 250 top-level functions and properties do not exceed Lua local or upvalue limits`() {
        val count = 250
        val code =
            buildString {
                appendLine("fun f0(): Int = 1")
                for (i in 1 until count) {
                    appendLine("fun f$i(): Int = f${i - 1}() + 1")
                }
                for (i in 0 until count) {
                    appendLine("val v$i: Int = f$i()")
                }
                appendLine(
                    """
                    fun box(): String {
                        val ok = v0 == 1 && v${count - 1} == $count && f${count - 1}() == $count
                        return if (ok) "OK" else "FAIL"
                    }
                    """.trimIndent(),
                )
            }

        assertBoxOk(code)
    }
}
