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

class DelegationBoxTest : AbstractKotluaTest() {
    @Test
    fun `interface delegation with property and partial override`() {
        assertBoxOk(
            """
            interface Service {
                val prefix: String
                fun first(): String
                fun second(): String
            }

            class RealService : Service {
                override val prefix: String = "O"
                override fun first(): String = prefix
                override fun second(): String = "X"
            }

            class Wrapper(service: Service) : Service by service {
                override fun second(): String = "K"
            }

            fun box(): String {
                val wrapper: Service = Wrapper(RealService())
                return wrapper.first() + wrapper.second()
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `multiple interface delegation with overloaded methods`() {
        assertBoxOk(
            """
            interface Alpha {
                fun emit(x: Int): String
            }

            interface Beta {
                fun emit(x: String): String
            }

            class AlphaImpl : Alpha {
                override fun emit(x: Int): String = if (x == 1) "O" else "X"
            }

            class BetaImpl : Beta {
                override fun emit(x: String): String = x
            }

            class Combined(a: Alpha, b: Beta) : Alpha by a, Beta by b

            fun box(): String {
                val combined = Combined(AlphaImpl(), BetaImpl())
                return combined.emit(1) + combined.emit("K")
            }
            """.trimIndent(),
        )
    }

    @Test
    @Disabled("TODO: Classes, property delegation (getValue/setValue) & kotlin.reflect.KProperty")
    fun `property delegation using getValue and setValue`() {
        assertBoxOk(
            """
            class IntCell(private var stored: Int) {
                operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Int = stored

                operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Int) {
                    stored = value
                }
            }

            class Config {
                var port: Int by IntCell(40)
            }

            fun box(): String {
                val config = Config()
                config.port = config.port + 2
                return if (config.port == 42) "OK" else "FAIL"
            }
            """.trimIndent(),
        )
    }
}
