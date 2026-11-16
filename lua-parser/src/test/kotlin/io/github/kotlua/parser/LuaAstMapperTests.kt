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
package io.github.kotlua.parser

import io.github.kotlua.LuaToStringGenerator
import io.github.kotlua.ast.LuaBinaryOperator
import io.github.kotlua.ast.LuaBlock
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaLValue
import io.github.kotlua.ast.LuaName
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.LuaUnaryOperator
import io.github.kotlua.ast.scope.LuaRootScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LuaAstMapperTests {
    private fun parseWithScope(code: String): Pair<List<LuaStatement>, LuaRootScope> {
        val rootScope = LuaRootScope()
        val statements = LuaAstMapper(rootScope).mapCode(code)
        return statements to rootScope
    }

    private fun parseSingleExpression(code: String): LuaExpression {
        val (statements, _) = parseWithScope(code)
        assertEquals(1, statements.size, "Expected single Return statement for expression '$code'")
        val ret = statements.single() as LuaStatement.Return
        return ret.values.single()
    }

    private fun roundTrip(code: String): String {
        val (statements, scope) = parseWithScope(code)
        return LuaToStringGenerator.generate(LuaBlock(statements, scope))
    }

    @Nested
    inner class ExpressionsAndPrecedence {
        @Test
        fun `single expression wraps result in Return statement`() {
            val expr = parseSingleExpression("a + b * c") as LuaExpression.BinaryOp

            assertEquals(LuaBinaryOperator.Add, expr.op)
            assertEquals("a", (expr.left as LuaName).identifier)

            val right = expr.right as LuaExpression.BinaryOp
            assertEquals(LuaBinaryOperator.Multiply, right.op)
            assertEquals("b", (right.left as LuaName).identifier)
            assertEquals("c", (right.right as LuaName).identifier)
        }

        @Test
        fun `power and concat are right-associative`() {
            val powExpr = parseSingleExpression("a ^ b ^ c") as LuaExpression.BinaryOp
            assertEquals(LuaBinaryOperator.Power, powExpr.op)
            assertEquals("a", (powExpr.left as LuaName).identifier)
            assertTrue(powExpr.right is LuaExpression.BinaryOp)

            val concatExpr = parseSingleExpression("a .. b .. c") as LuaExpression.BinaryOp
            assertEquals(LuaBinaryOperator.Concat, concatExpr.op)
            assertEquals("a", (concatExpr.left as LuaName).identifier)
            assertTrue(concatExpr.right is LuaExpression.BinaryOp)
        }

        @Test
        fun `power binds tighter than unary minus`() {
            val expr = parseSingleExpression("-a ^ b") as LuaExpression.UnaryOp
            assertEquals(LuaUnaryOperator.Negate, expr.op)

            val innerPow = expr.operand as LuaExpression.BinaryOp
            assertEquals(LuaBinaryOperator.Power, innerPow.op)
            assertEquals("a", (innerPow.left as LuaName).identifier)
            assertEquals("b", (innerPow.right as LuaName).identifier)
        }

        @Test
        fun `empty or comment-only code produces empty statement list`() {
            val (emptyStmts, emptyScope) = parseWithScope("   \n  ")
            assertTrue(emptyStmts.isEmpty())
            assertTrue(emptyScope.ownNames.isEmpty())

            val (commentStmts, commentScope) = parseWithScope("-- just a comment\n--[[ block comment ]]")
            assertTrue(commentStmts.isEmpty())
            assertTrue(commentScope.ownNames.isEmpty())
        }
    }

    @Nested
    inner class ScopesAndFreeVariables {
        @Test
        fun `free variables land in root scope while locals stay isolated`() {
            val code =
                """
                local a = ktVar1 + 1
                local b = a + ktVar2
                return b
                """.trimIndent()

            val (_, rootScope) = parseWithScope(code)

            assertEquals(setOf("ktVar1", "ktVar2"), rootScope.ownNames.keys)
            assertFalse("a" in rootScope.ownNames)
            assertFalse("b" in rootScope.ownNames)
        }

        @Test
        fun `local self-shadowing captures outer variable on RHS and creates distinct LHS name`() {
            val code = "local x = x"
            val (statements, rootScope) = parseWithScope(code)

            assertEquals(setOf("x"), rootScope.ownNames.keys)

            val decl = statements.single() as LuaStatement.LocalVarDeclaration
            val lhsName = decl.names.single()
            val rhsName = decl.initializers.single() as LuaName

            assertEquals("x", lhsName.identifier)
            assertEquals("x", rhsName.identifier)
            assertSame(rootScope.ownNames["x"], rhsName)
            assertNotEquals(lhsName, rhsName)
        }

        @Test
        fun `nested do-end block shadows outer variable without mutating outer binding`() {
            val code =
                """
                local a = ext1
                do
                    local a = ext2
                    local b = a
                end
                return a
                """.trimIndent()

            val (statements, rootScope) = parseWithScope(code)

            assertEquals(setOf("ext1", "ext2"), rootScope.ownNames.keys)

            val outerDecl = statements[0] as LuaStatement.LocalVarDeclaration
            val doBlock = statements[1] as LuaBlock
            val innerDecl = doBlock.statements[0] as LuaStatement.LocalVarDeclaration
            val innerUse = doBlock.statements[1] as LuaStatement.LocalVarDeclaration
            val ret = statements[2] as LuaStatement.Return

            assertSame(innerDecl.names.single(), innerUse.initializers.single())
            assertSame(outerDecl.names.single(), ret.values.single())
            assertNotEquals(outerDecl.names.single(), innerDecl.names.single())
        }

        @Test
        fun `recursive local function binds self-reference to local function name`() {
            val code =
                """
                local function fact(n)
                    if n <= 1 then
                        return extBase
                    end
                    return n * fact(n - 1)
                end
                return fact(extArg)
                """.trimIndent()

            val (_, rootScope) = parseWithScope(code)

            assertEquals(setOf("extBase", "extArg"), rootScope.ownNames.keys)
            assertFalse("fact" in rootScope.ownNames)
            assertFalse("n" in rootScope.ownNames)
        }

        @Test
        fun `repeat-until condition sees local variables declared inside loop body`() {
            val code =
                """
                repeat
                    local done = check(ext1)
                until done or ext2
                """.trimIndent()

            val (statements, rootScope) = parseWithScope(code)

            assertEquals(setOf("check", "ext1", "ext2"), rootScope.ownNames.keys)
            assertFalse("done" in rootScope.ownNames)

            val repeatStmt = statements.single() as LuaStatement.RepeatUntil
            val localDecl = repeatStmt.body.statements.single() as LuaStatement.LocalVarDeclaration
            val condOr = repeatStmt.condition as LuaExpression.BinaryOp
            assertSame(localDecl.names.single(), condOr.left)
        }

        @Test
        fun `colon method declaration binds implicit self and hides parameters from root scope`() {
            val code =
                """
                function myObj:compute(arg1, arg2)
                    return self.base + arg1 + arg2 + extOffset
                end
                """.trimIndent()

            val (statements, rootScope) = parseWithScope(code)

            assertEquals(setOf("myObj", "extOffset"), rootScope.ownNames.keys)
            assertFalse("self" in rootScope.ownNames)
            assertFalse("arg1" in rootScope.ownNames)
            assertFalse("arg2" in rootScope.ownNames)
            assertFalse("compute" in rootScope.ownNames)

            val fnDecl = statements.single() as LuaStatement.FunctionDeclaration
            assertEquals("compute", fnDecl.methodName)
            assertSame(rootScope.ownNames["myObj"], fnDecl.target)
        }

        @Test
        fun `numeric and generic for loops keep loop variables out of root scope`() {
            val code =
                """
                for i = 1, extLimit do
                    local v = i
                end
                for k, v in pairs(extTable) do
                    consume(k, v)
                end
                """.trimIndent()

            val (_, rootScope) = parseWithScope(code)

            assertEquals(setOf("extLimit", "pairs", "extTable", "consume"), rootScope.ownNames.keys)
            assertFalse("i" in rootScope.ownNames)
            assertFalse("k" in rootScope.ownNames)
            assertFalse("v" in rootScope.ownNames)
        }

        @Test
        fun `same free variable used multiple times shares the same LuaName instance`() {
            val code = "return ext + ext * 2"
            val (statements, rootScope) = parseWithScope(code)

            val ret = statements.single() as LuaStatement.Return
            val add = ret.values.single() as LuaExpression.BinaryOp
            val mul = add.right as LuaExpression.BinaryOp

            assertSame(rootScope.ownNames["ext"], add.left)
            assertSame(add.left, mul.left)
        }
    }

    @Nested
    inner class LiteralsAndComments {
        @Test
        fun `parses primitive literals and varargs`() {
            val code = "return nil, true, false, 42, 3.14, ..."
            val (statements, _) = parseWithScope(code)

            val ret = statements.single() as LuaStatement.Return
            assertEquals(6, ret.values.size)
            assertEquals(LuaExpression.NilLiteral, ret.values[0])
            assertEquals(LuaExpression.BooleanLiteral(true), ret.values[1])
            assertEquals(LuaExpression.BooleanLiteral(false), ret.values[2])
            assertEquals(LuaExpression.IntLiteral(42), ret.values[3])
            assertEquals(LuaExpression.DoubleLiteral(3.14), ret.values[4])
            assertEquals(LuaExpression.VarargLiteral, ret.values[5])
        }

        @Test
        fun `parses long strings and nested bracket strings without treating comments as code`() {
            val validCode =
                """
                --[=[
                    nested [[comment]] should be ignored
                ]=]
                local s1 = [[hello world]]
                local s2 = [=[nested [[brackets]] inside]=]
                return s1, s2
                """.trimIndent()

            val (statements, rootScope) = parseWithScope(validCode)
            assertTrue(rootScope.ownNames.isEmpty())
            assertEquals(3, statements.size)

            val s1 = (statements[0] as LuaStatement.LocalVarDeclaration).initializers.single() as LuaExpression.StringLiteral
            val s2 = (statements[1] as LuaStatement.LocalVarDeclaration).initializers.single() as LuaExpression.StringLiteral
            assertEquals("hello world", s1.value)
            assertEquals("nested [[brackets]] inside", s2.value)
        }
    }

    @Nested
    inner class CallsAndTables {
        @Test
        fun `parses chained field, call, index, and method expressions`() {
            val expr = parseSingleExpression("a.b(1)[2]:c(3)")
            val methodC = expr as LuaExpression.MethodCall
            assertEquals("c", methodC.methodName)

            val index2 = methodC.target as LuaLValue.TableIndex
            assertEquals(LuaExpression.IntLiteral(2), index2.index)

            val callB = index2.target as LuaExpression.FunctionCall
            val fieldB = callB.target as LuaLValue.TableField
            assertEquals("b", fieldB.fieldName)
            assertEquals("a", (fieldB.target as LuaName).identifier)
        }

        @Test
        fun `parses Lua syntactic sugar for string and table function call arguments`() {
            val code =
                """
                print 'hello'
                setup { 10, key = true }
                """.trimIndent()

            val (statements, rootScope) = parseWithScope(code)
            assertEquals(setOf("print", "setup"), rootScope.ownNames.keys)
            assertEquals(2, statements.size)
            assertTrue(statements[0] is LuaExpression.FunctionCall)
            assertTrue(statements[1] is LuaExpression.FunctionCall)
        }

        @Test
        fun `parses semicolon-separated statements and IIFE cleanly`() {
            val code =
                """
                local a = func()
                ;(function(b)
                    return b + 5
                end)(3)
                """.trimIndent()

            val (statements, rootScope) = parseWithScope(code)
            assertEquals(setOf("func"), rootScope.ownNames.keys)
            assertEquals(2, statements.size)
            assertTrue(statements[0] is LuaStatement.LocalVarDeclaration)
            assertTrue(statements[1] is LuaExpression.FunctionCall)
        }
    }

    @Nested
    inner class RoundTrip {
        @Test
        fun `parsed AST prints to valid Lua 5_1 and reparses identically`() {
            val source =
                """
                local function fib(n)
                    if n <= 1 then
                        return n
                    else
                        return fib(n - 1) + fib(n - 2)
                    end
                end
                local result = fib(10)
                ;(function(x)
                    print(x)
                end)(result)
                repeat
                    result = result - 1
                until result == 0
                """.trimIndent()

            val firstPass = roundTrip(source)
            val secondPass = roundTrip(firstPass)

            assertEquals(firstPass, secondPass)
        }
    }

    @Nested
    inner class SyntaxErrors {
        @Test
        fun `throws IllegalStateException on unclosed block or string`() {
            assertThrows(IllegalStateException::class.java) {
                LuaAstMapper().mapCode("if true then return 1")
            }

            assertThrows(IllegalStateException::class.java) {
                LuaAstMapper().mapCode("local s = 'unclosed")
            }

            assertThrows(IllegalStateException::class.java) {
                LuaAstMapper().mapCode("local s = [[unclosed long string")
            }
        }

        @Test
        fun `throws IllegalStateException on Lua 5_2 goto labels and invalid assignments`() {
            assertThrows(IllegalStateException::class.java) {
                LuaAstMapper().mapCode("::my_label:: goto my_label")
            }

            assertThrows(IllegalStateException::class.java) {
                LuaAstMapper().mapCode("123 = 456")
            }
        }
    }
}
