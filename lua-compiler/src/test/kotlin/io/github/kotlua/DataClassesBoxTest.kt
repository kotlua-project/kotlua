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

class DataClassesBoxTest : AbstractKotluaTest() {
    @Test
    fun `componentN destructuring and copy with default parameters`() {
        assertBoxOk(
            """
            data class Point3D(val x: Int, val y: Int, val z: Int = 0)

            fun box(): String {
                val p1 = Point3D(10, 20)
                val (x1, y1, z1) = p1

                val p2 = p1.copy(y = 99)
                val (x2, y2, z2) = p2

                val p3 = p1.copy(x = 1, z = 5)
                val (x3, y3, z3) = p3

                return if (x1 == 10 && y1 == 20 && z1 == 0 &&
                    x2 == 10 && y2 == 99 && z2 == 0 &&
                    x3 == 1 && y3 == 20 && z3 == 5
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
    fun `data class equals hashCode and toString with nested instances`() {
        assertBoxOk(
            """
            data class Address(val city: String, val zip: Int)

            data class Person(val name: String, val age: Int, val address: Address?)

            fun box(): String {
                val a1 = Address("Berlin", 10115)
                val a2 = Address("Berlin", 10115)
                val a3 = Address("Munich", 80331)

                val p1 = Person("Alice", 30, a1)
                val p2 = Person("Alice", 30, a2)
                val p3 = Person("Alice", 30, a3)
                val p4 = Person("Bob", 25, null)

                val eq12 = p1 == p2
                val eq13 = p1 == p3
                val hash12 = p1.hashCode() == p2.hashCode()

                val s1 = p1.toString()
                val s4 = p4.toString()

                val expectedS1 = "Person(name=Alice, age=30, address=Address(city=Berlin, zip=10115))"
                val expectedS4 = "Person(name=Bob, age=25, address=null)"

                return if (eq12 && !eq13 && hash12 && s1 == expectedS1 && s4 == expectedS4) {
                    "OK"
                } else {
                    "FAIL:" + eq12 + ":" + eq13 + ":" + hash12 + "\n" + s1 + "\n" + s4
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `body properties do not participate in generated equals and hashCode`() {
        assertBoxOk(
            """
            data class Account(val id: Int) {
                var balance: Int = 0
            }

            fun box(): String {
                val acc1 = Account(1)
                acc1.balance = 500

                val acc2 = Account(1)
                acc2.balance = 9999

                val acc3 = Account(2)
                acc3.balance = 500

                val eq12 = acc1 == acc2
                val eq13 = acc1 == acc3
                val hash12 = acc1.hashCode() == acc2.hashCode()
                val s = acc1.toString()

                return if (eq12 && !eq13 && hash12 && s == "Account(id=1)") {
                    "OK"
                } else {
                    "FAIL:" + eq12 + ":" + eq13 + ":" + hash12 + ":" + s
                }
            }
            """.trimIndent(),
        )
    }
}
