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
package io.github.kotlua.ast

import io.github.kotlua.LuaTextOutput
import io.github.kotlua.LuaToStringGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LuaAstTests {
    @Test
    fun `correctly generates code`() {
        val program =
            LuaBlock(
                statements =
                    listOf(
                        LuaStatement.LocalVarDeclaration(
                            names = listOf("a"),
                            initializers =
                                listOf(
                                    LuaExpression.IntLiteral(0),
                                ),
                        ),
                        LuaStatement.While(
                            condition =
                                LuaExpression.BinaryOp(
                                    left = LuaName("a"),
                                    op = LuaBinaryOperator.Lt,
                                    right = LuaExpression.IntLiteral(10),
                                ),
                            body =
                                LuaBlock(
                                    statements =
                                        listOf(
                                            LuaStatement.Assignment(
                                                targets =
                                                    listOf(
                                                        LuaName("a"),
                                                    ),
                                                values =
                                                    listOf(
                                                        LuaExpression.BinaryOp(
                                                            left = LuaName("a"),
                                                            op = LuaBinaryOperator.Add,
                                                            right = LuaExpression.IntLiteral(1),
                                                        ),
                                                    ),
                                            ),
                                        ),
                                ),
                        ),
                        LuaExpression.FunctionCall(
                            target = LuaName("print"),
                            args =
                                listOf(
                                    LuaExpression.StringLiteral("a ="),
                                    LuaName("a"),
                                ),
                        ),
                    ),
            )
        val actual = LuaToStringGenerator.generate(program, LuaTextOutput(indentString = "    "))

        val expected =
            """
            local a = 0
            while (a < 10) do
                a = (a + 1)
            end
            print('a =', a)
            
            """.trimIndent()

        assertEquals(expected, actual)
    }
}
