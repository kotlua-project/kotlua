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

import io.github.kotlua.LuaToStringGenerator
import io.github.kotlua.ast.scope.LuaFunctionScope
import io.github.kotlua.ast.scope.LuaRootScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LuaAstTests {
    private fun renderExpr(expression: LuaExpression): String = LuaToStringGenerator.generateExpression(expression)

    private fun renderStmt(statement: LuaStatement): String = LuaToStringGenerator.generateStatement(statement)

    private fun renderProgram(vararg statements: LuaStatement): String {
        val program = LuaBlock(statements.toList(), LuaRootScope())
        return LuaToStringGenerator.generate(program).trimEnd()
    }

    @Nested
    inner class NameIdentity {
        @Test
        fun `LuaName uses referential equality instead of structural equality`() {
            val first = LuaName("x")
            val second = LuaName("x")

            assertEquals(first, first)
            assertNotEquals(first, second)
        }
    }

    @Nested
    inner class Operators {
        @Test
        fun `nested unary minus wraps inner negation in parentheses to prevent Lua comment`() {
            val expression =
                LuaExpression.UnaryOp(
                    LuaUnaryOperator.Negate,
                    LuaExpression.UnaryOp(LuaUnaryOperator.Negate, LuaName("x")),
                )
            assertEquals("-(-x)", renderExpr(expression))
        }

        @Test
        fun `unary minus on negative number literal wraps literal to prevent Lua comment`() {
            val expression =
                LuaExpression.UnaryOp(
                    LuaUnaryOperator.Negate,
                    LuaExpression.IntLiteral(-5),
                )
            assertEquals("-(-5)", renderExpr(expression))
        }

        @Test
        fun `unary minus applied to power expression omits redundant parentheses`() {
            val expression =
                LuaExpression.UnaryOp(
                    LuaUnaryOperator.Negate,
                    LuaExpression.BinaryOp(LuaName("a"), LuaBinaryOperator.Power, LuaName("b")),
                )
            assertEquals("-a ^ b", renderExpr(expression))
        }

        @Test
        fun `power expression with unary minus or negative literal base wraps base in parentheses`() {
            val negVarPow =
                LuaExpression.BinaryOp(
                    LuaExpression.UnaryOp(LuaUnaryOperator.Negate, LuaName("a")),
                    LuaBinaryOperator.Power,
                    LuaName("b"),
                )
            val negLitPow =
                LuaExpression.BinaryOp(
                    LuaExpression.IntLiteral(-2),
                    LuaBinaryOperator.Power,
                    LuaExpression.IntLiteral(2),
                )
            assertEquals("(-a) ^ b", renderExpr(negVarPow))
            assertEquals("(-2) ^ 2", renderExpr(negLitPow))
        }

        @Test
        fun `binary operator precedence omits outer and redundant inner parentheses`() {
            // a + b * c
            val noParens =
                LuaExpression.BinaryOp(
                    LuaName("a"),
                    LuaBinaryOperator.Add,
                    LuaExpression.BinaryOp(LuaName("b"), LuaBinaryOperator.Multiply, LuaName("c")),
                )
            // (a + b) * c
            val withParens =
                LuaExpression.BinaryOp(
                    LuaExpression.BinaryOp(LuaName("a"), LuaBinaryOperator.Add, LuaName("b")),
                    LuaBinaryOperator.Multiply,
                    LuaName("c"),
                )
            assertEquals("a + b * c", renderExpr(noParens))
            assertEquals("(a + b) * c", renderExpr(withParens))
        }

        @Test
        fun `left-associative subtraction wraps right child of same precedence`() {
            // (a - b) - c -> a - b - c
            val leftGrouped =
                LuaExpression.BinaryOp(
                    LuaExpression.BinaryOp(LuaName("a"), LuaBinaryOperator.Subtract, LuaName("b")),
                    LuaBinaryOperator.Subtract,
                    LuaName("c"),
                )
            // a - (b - c) -> a - (b - c)
            val rightGrouped =
                LuaExpression.BinaryOp(
                    LuaName("a"),
                    LuaBinaryOperator.Subtract,
                    LuaExpression.BinaryOp(LuaName("b"), LuaBinaryOperator.Subtract, LuaName("c")),
                )
            assertEquals("a - b - c", renderExpr(leftGrouped))
            assertEquals("a - (b - c)", renderExpr(rightGrouped))
        }

        @Test
        fun `right-associative power and concat wrap left child and omit parens on right`() {
            val leftPow =
                LuaExpression.BinaryOp(
                    LuaExpression.BinaryOp(LuaName("a"), LuaBinaryOperator.Power, LuaName("b")),
                    LuaBinaryOperator.Power,
                    LuaName("c"),
                )
            val rightPow =
                LuaExpression.BinaryOp(
                    LuaName("a"),
                    LuaBinaryOperator.Power,
                    LuaExpression.BinaryOp(LuaName("b"), LuaBinaryOperator.Power, LuaName("c")),
                )
            val rightConcat =
                LuaExpression.BinaryOp(
                    LuaName("a"),
                    LuaBinaryOperator.Concat,
                    LuaExpression.BinaryOp(LuaName("b"), LuaBinaryOperator.Concat, LuaName("c")),
                )
            assertEquals("(a ^ b) ^ c", renderExpr(leftPow))
            assertEquals("a ^ b ^ c", renderExpr(rightPow))
            assertEquals("a .. b .. c", renderExpr(rightConcat))
        }
    }

    @Nested
    inner class Literals {
        @Test
        fun `primitive literals render to valid Lua 5_1 representations`() {
            assertEquals("nil", renderExpr(LuaExpression.NilLiteral))
            assertEquals("true", renderExpr(LuaExpression.BooleanLiteral(true)))
            assertEquals("false", renderExpr(LuaExpression.BooleanLiteral(false)))
            assertEquals("42", renderExpr(LuaExpression.IntLiteral(42)))
            assertEquals("3.14", renderExpr(LuaExpression.DoubleLiteral(3.14)))
            assertEquals("...", renderExpr(LuaExpression.VarargLiteral))
        }

        @Test
        fun `string literal escapes single quotes, backslashes, and control characters`() {
            val expr = LuaExpression.StringLiteral("line1\n\t'single' \"double\"\\end")
            assertEquals("'line1\\n\\t\\'single\\' \"double\"\\\\end'", renderExpr(expr))
        }
    }

    @Nested
    inner class PrefixExpressionsAndCalls {
        @Test
        fun `immediately invoked anonymous function wraps function target in parentheses`() {
            val scope = LuaFunctionScope(LuaRootScope())
            val anonFn =
                LuaExpression.AnonymousFunction(
                    parameters = emptyList(),
                    block = LuaBlock(listOf(LuaStatement.Return(listOf(LuaExpression.IntLiteral(1)))), scope),
                    scope = scope,
                    isVararg = false,
                )
            val call = LuaExpression.FunctionCall(target = anonFn, args = emptyList())

            val actual = renderExpr(call)
            assertEquals(
                """
                (function()
                    return 1
                end)()
                """.trimIndent(),
                actual,
            )
        }

        @Test
        fun `method call on string literal wraps target in parentheses`() {
            val call =
                LuaExpression.MethodCall(
                    target = LuaExpression.StringLiteral("abc"),
                    methodName = "len",
                    args = emptyList(),
                )

            assertEquals("('abc'):len()", renderExpr(call))
        }

        @Test
        fun `method call on table constructor wraps target in parentheses`() {
            val call =
                LuaExpression.MethodCall(
                    target = LuaExpression.TableConstructor(emptyList()),
                    methodName = "foo",
                    args = listOf(LuaExpression.IntLiteral(1)),
                )

            assertEquals("({}):foo(1)", renderExpr(call))
        }

        @Test
        fun `chained prefix expressions do not add redundant parentheses`() {
            // a.b(1)[2]:c(3)
            val fieldAccess = LuaLValue.TableField(LuaName("a"), "b")
            val callB = LuaExpression.FunctionCall(fieldAccess, listOf(LuaExpression.IntLiteral(1)))
            val index2 = LuaLValue.TableIndex(callB, LuaExpression.IntLiteral(2))
            val methodC = LuaExpression.MethodCall(index2, "c", listOf(LuaExpression.IntLiteral(3)))

            assertEquals("a.b(1)[2]:c(3)", renderExpr(methodC))
        }
    }

    @Nested
    inner class Tables {
        @Test
        fun `empty table constructor renders as empty braces`() {
            assertEquals("{}", renderExpr(LuaExpression.TableConstructor(emptyList())))
        }

        @Test
        fun `table constructor renders positional, named, and indexed fields`() {
            val table =
                LuaExpression.TableConstructor(
                    listOf(
                        LuaField.Positional(LuaExpression.IntLiteral(10)),
                        LuaField.Named("x", LuaExpression.IntLiteral(20)),
                        LuaField.Indexed(LuaExpression.StringLiteral("key-1"), LuaExpression.BooleanLiteral(true)),
                    ),
                )

            assertEquals("{ 10; x = 20; ['key-1'] = true }", renderExpr(table))
        }
    }

    @Nested
    inner class Statements {
        @Test
        fun `local variable declaration without initializer omits equals sign`() {
            val stmt =
                LuaStatement.LocalVarDeclaration(
                    names = listOf(LuaName("x")),
                    initializers = emptyList(),
                )

            assertEquals("local x", renderStmt(stmt))
        }

        @Test
        fun `local variable declaration with initializer prints assignment`() {
            val stmt =
                LuaStatement.LocalVarDeclaration(
                    names = listOf(LuaName("x")),
                    initializers = listOf(LuaExpression.IntLiteral(5)),
                )

            assertEquals("local x = 5", renderStmt(stmt))
        }

        @Test
        fun `composite block emits inner statements directly without do-end wrapper`() {
            val composite =
                LuaCompositeBlock(
                    listOf(
                        LuaStatement.LocalVarDeclaration(listOf(LuaName("a")), listOf(LuaExpression.IntLiteral(1))),
                        LuaStatement.Return(listOf(LuaName("a"))),
                    ),
                )

            assertEquals(
                """
                local a = 1
                return a
                """.trimIndent(),
                renderStmt(composite),
            )
        }

        @Test
        fun `function declaration prints global and method syntax properly`() {
            val scope = LuaFunctionScope(LuaRootScope())
            val body = LuaBlock(listOf(LuaStatement.Return(listOf(LuaName("x")))), scope)
            val fn =
                LuaExpression.AnonymousFunction(
                    parameters = listOf(LuaName("x")),
                    block = body,
                    isVararg = false,
                    scope = scope,
                )

            val globalDecl =
                LuaStatement.FunctionDeclaration(
                    target = LuaName("foo"),
                    methodName = null,
                    function = fn,
                )

            assertEquals(
                """
                function foo(x)
                    return x
                end
                """.trimIndent(),
                renderStmt(globalDecl),
            )

            val methodDecl =
                LuaStatement.FunctionDeclaration(
                    target = LuaName("MyClass"),
                    methodName = "bar",
                    function = fn,
                )
            assertEquals(
                """
                function MyClass:bar(x)
                    return x
                end
                """.trimIndent(),
                renderStmt(methodDecl),
            )
        }

        @Test
        fun `empty statement produces no output`() {
            assertEquals("", renderStmt(LuaStatement.Empty))
        }
    }

    @Nested
    inner class SemicolonDisambiguation {
        private fun makeIife(returnValue: Int = 5): LuaExpression.FunctionCall {
            val fnScope = LuaFunctionScope(LuaRootScope())
            val param = LuaName("b")
            val body =
                LuaBlock(
                    listOf(
                        LuaStatement.Return(
                            listOf(LuaExpression.BinaryOp(param, LuaBinaryOperator.Add, LuaExpression.IntLiteral(returnValue))),
                        ),
                    ),
                    fnScope,
                )
            val anonFn =
                LuaExpression.AnonymousFunction(
                    parameters = listOf(param),
                    block = body,
                    scope = fnScope,
                    isVararg = false,
                )
            return LuaExpression.FunctionCall(target = anonFn, args = listOf(LuaExpression.IntLiteral(3)))
        }

        @Test
        fun `inserts semicolon before IIFE after local variable initialization`() {
            val localDecl =
                LuaStatement.LocalVarDeclaration(
                    names = listOf(LuaName("a")),
                    initializers = listOf(LuaExpression.FunctionCall(LuaName("func"), emptyList())),
                )
            val iife = makeIife()

            assertEquals(
                """
                local a = func()
                ;(function(b)
                    return b + 5
                end)(3)
                """.trimIndent(),
                renderProgram(localDecl, iife),
            )
        }

        @Test
        fun `inserts semicolon before IIFE after assignment and function call statements`() {
            val assign =
                LuaStatement.Assignment(
                    targets = listOf(LuaName("a")),
                    values = listOf(LuaName("x")),
                )
            val call = LuaExpression.FunctionCall(LuaName("foo"), emptyList())
            val iife = makeIife()

            assertEquals(
                """
                a = x
                foo()
                ;(function(b)
                    return b + 5
                end)(3)
                """.trimIndent(),
                renderProgram(assign, call, iife),
            )
        }

        @Test
        fun `inserts semicolon before method call on literal or parenthesized assignment target`() {
            val call = LuaExpression.FunctionCall(LuaName("func"), emptyList())
            val stringMethodCall =
                LuaExpression.MethodCall(
                    target = LuaExpression.StringLiteral("abc"),
                    methodName = "len",
                    args = emptyList(),
                )
            val parenthesizedAssign =
                LuaStatement.Assignment(
                    targets =
                        listOf(
                            LuaLValue.TableField(
                                target = LuaExpression.TableConstructor(emptyList()),
                                fieldName = "x",
                            ),
                        ),
                    values = listOf(LuaExpression.IntLiteral(1)),
                )

            assertEquals(
                """
                func()
                ;('abc'):len()
                ;({}).x = 1
                """.trimIndent(),
                renderProgram(call, stringMethodCall, parenthesizedAssign),
            )
        }

        @Test
        fun `inserts semicolon before IIFE after repeat-until loop because until ends with expression`() {
            val scope = LuaRootScope()
            val repeatUntil =
                LuaStatement.RepeatUntil(
                    body =
                        LuaBlock(
                            listOf(LuaExpression.FunctionCall(LuaName("step"), emptyList())),
                            scope,
                        ),
                    condition = LuaExpression.FunctionCall(LuaName("done"), emptyList()),
                )
            val iife = makeIife()

            assertEquals(
                """
                repeat
                    step()
                until done()
                ;(function(b)
                    return b + 5
                end)(3)
                """.trimIndent(),
                renderProgram(repeatUntil, iife),
            )
        }

        @Test
        fun `preserves expression tracking across LuaCompositeBlock and Empty statements`() {
            val localDecl =
                LuaStatement.LocalVarDeclaration(
                    names = listOf(LuaName("a")),
                    initializers = listOf(LuaExpression.IntLiteral(1)),
                )
            val compositeWithIife =
                LuaCompositeBlock(
                    listOf(
                        LuaStatement.Empty,
                        makeIife(),
                        LuaStatement.Assignment(listOf(LuaName("a")), listOf(LuaExpression.IntLiteral(2))),
                    ),
                )
            val trailingIife = makeIife()

            assertEquals(
                """
                local a = 1
                ;(function(b)
                    return b + 5
                end)(3)
                a = 2
                ;(function(b)
                    return b + 5
                end)(3)
                """.trimIndent(),
                renderProgram(localDecl, compositeWithIife, LuaStatement.Empty, trailingIife),
            )
        }

        @Test
        fun `does not insert leading semicolon at the start of a program`() {
            val iife = makeIife()

            assertEquals(
                """
                (function(b)
                    return b + 5
                end)(3)
                """.trimIndent(),
                renderProgram(LuaStatement.Empty, iife),
            )
        }

        @Test
        fun `resets expression tracking inside nested while, if, do-end, and function blocks`() {
            val rootScope = LuaRootScope()
            val fnScope = LuaFunctionScope(rootScope)

            val precedingAssign =
                LuaStatement.LocalVarDeclaration(
                    names = listOf(LuaName("a")),
                    initializers = listOf(LuaExpression.FunctionCall(LuaName("func"), emptyList())),
                )
            val whileStmt =
                LuaStatement.While(
                    condition = LuaExpression.BooleanLiteral(true),
                    body = LuaBlock(listOf(makeIife()), rootScope),
                )
            val ifStmt =
                LuaStatement.If(
                    condition = LuaName("cond"),
                    thenBlock = LuaBlock(listOf(makeIife()), rootScope),
                    elseIfs = emptyList(),
                    elseBlock = LuaBlock(listOf(makeIife()), rootScope),
                )
            val doBlock = LuaBlock(listOf(makeIife()), rootScope)
            val fnDecl =
                LuaStatement.FunctionDeclaration(
                    target = LuaName("testFn"),
                    methodName = null,
                    function =
                        LuaExpression.AnonymousFunction(
                            parameters = emptyList(),
                            block = LuaBlock(listOf(makeIife()), fnScope),
                            scope = fnScope,
                            isVararg = false,
                        ),
                )

            assertEquals(
                """
                local a = func()
                while true do
                    (function(b)
                        return b + 5
                    end)(3)
                end
                if cond then
                    (function(b)
                        return b + 5
                    end)(3)
                else
                    (function(b)
                        return b + 5
                    end)(3)
                end
                do
                    (function(b)
                        return b + 5
                    end)(3)
                end
                function testFn()
                    (function(b)
                        return b + 5
                    end)(3)
                end
                """.trimIndent(),
                renderProgram(precedingAssign, whileStmt, ifStmt, doBlock, fnDecl),
            )
        }

        @Test
        fun `does not insert semicolon after end keyword or uninitialized local declaration`() {
            val rootScope = LuaRootScope()
            val uninitLocal =
                LuaStatement.LocalVarDeclaration(
                    names = listOf(LuaName("x")),
                    initializers = emptyList(),
                )
            val doBlock =
                LuaBlock(
                    listOf(LuaStatement.Assignment(listOf(LuaName("x")), listOf(LuaExpression.IntLiteral(1)))),
                    rootScope,
                )

            assertEquals(
                """
                local x
                (function(b)
                    return b + 5
                end)(3)
                do
                    x = 1
                end
                (function(b)
                    return b + 5
                end)(3)
                """.trimIndent(),
                renderProgram(uninitLocal, makeIife(), doBlock, makeIife()),
            )
        }

        @Test
        fun `does not insert semicolon before regular LValue call after expression statement`() {
            val localDecl =
                LuaStatement.LocalVarDeclaration(
                    names = listOf(LuaName("a")),
                    initializers = listOf(LuaExpression.FunctionCall(LuaName("func"), emptyList())),
                )
            val regularCall = LuaExpression.FunctionCall(LuaName("bar"), listOf(LuaName("a")))

            assertEquals(
                """
                local a = func()
                bar(a)
                """.trimIndent(),
                renderProgram(localDecl, regularCall),
            )
        }
    }
}
