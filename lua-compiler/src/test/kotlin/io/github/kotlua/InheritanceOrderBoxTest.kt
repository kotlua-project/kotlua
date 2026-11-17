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