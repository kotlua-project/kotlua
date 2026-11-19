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

class SealedHierarchiesBoxTest : AbstractKotluaTest() {
    @Test
    fun `sealed class hierarchy with object and data class in exhaustive when`() {
        assertBoxOk(
            """
            sealed class Result {
                data class Success(val value: Int) : Result()
                data class Error(val code: Int, val message: String) : Result()
                object Loading : Result()
            }

            private fun render(r: Result): String =
                when (r) {
                    is Result.Success -> "OK:" + r.value
                    is Result.Error -> "ERR:" + r.code + ":" + r.message
                    Result.Loading -> "LOADING"
                }

            fun box(): String {
                val s = render(Result.Success(42))
                val e = render(Result.Error(404, "Not Found"))
                val l = render(Result.Loading)

                val isRes1 = Result.Success(1) is Result
                val isRes2 = Result.Loading is Result

                return if (s == "OK:42" && e == "ERR:404:Not Found" && l == "LOADING" && isRes1 && isRes2) {
                    "OK"
                } else {
                    "FAIL:" + s + "|" + e + "|" + l
                }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `recursive sealed class tree evaluation`() {
        assertBoxOk(
            """
            sealed class Expr {
                data class Const(val v: Int) : Expr()
                data class Add(val left: Expr, val right: Expr) : Expr()
                data class Mul(val left: Expr, val right: Expr) : Expr()
                data class Neg(val operand: Expr) : Expr()
            }

            private fun eval(e: Expr): Int =
                when (e) {
                    is Expr.Const -> e.v
                    is Expr.Add -> eval(e.left) + eval(e.right)
                    is Expr.Mul -> eval(e.left) * eval(e.right)
                    is Expr.Neg -> -eval(e.operand)
                }

            fun box(): String {
                // (3 + 5) * -(2 + 4) = 8 * -6 = -48
                val expr =
                    Expr.Mul(
                        Expr.Add(Expr.Const(3), Expr.Const(5)),
                        Expr.Neg(Expr.Add(Expr.Const(2), Expr.Const(4))),
                    )

                val result = eval(expr)
                return if (result == -48) "OK" else "FAIL:" + result
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `sealed interface diamond and multiple implementations`() {
        assertBoxOk(
            """
            sealed interface Node {
                val id: String
            }

            sealed interface ActionNode : Node {
                fun execute(): Int
            }

            sealed interface ValueNode : Node {
                fun query(): String
            }

            data class ComputeNode(override val id: String, val multiplier: Int) : ActionNode {
                override fun execute(): Int = multiplier * 10
            }

            data class ConstantNode(override val id: String, val content: String) : ValueNode {
                override fun query(): String = content
            }

            data class HybridNode(override val id: String, val score: Int) : ActionNode, ValueNode {
                override fun execute(): Int = score + 1
                override fun query(): String = "hybrid:" + score
            }

            private fun processAction(a: ActionNode): Int =
                when (a) {
                    is ComputeNode -> a.execute()
                    is HybridNode -> a.execute() * 2
                }

            private fun processValue(v: ValueNode): String =
                when (v) {
                    is ConstantNode -> v.query()
                    is HybridNode -> v.query() + "!"
                }

            fun box(): String {
                val c = ComputeNode("c1", 3)
                val k = ConstantNode("k1", "val")
                val h = HybridNode("h1", 7)

                val a1 = processAction(c)
                val a2 = processAction(h)

                val v1 = processValue(k)
                val v2 = processValue(h)

                val okIs = (h is Node) && (h is ActionNode) && (h is ValueNode)

                return if (a1 == 30 && a2 == 16 && v1 == "val" && v2 == "hybrid:7!" && okIs) {
                    "OK"
                } else {
                    "FAIL"
                }
            }
            """.trimIndent(),
        )
    }
}
