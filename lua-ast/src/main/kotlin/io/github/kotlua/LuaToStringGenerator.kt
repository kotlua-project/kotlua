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

import io.github.kotlua.ast.LuaBinaryOperator
import io.github.kotlua.ast.LuaBlock
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaField
import io.github.kotlua.ast.LuaLValue
import io.github.kotlua.ast.LuaName
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.LuaUnaryOperator

class LuaToStringGenerator private constructor(
    val textOutput: LuaTextOutput,
) {
    companion object {
        fun generate(
            program: LuaBlock,
            textOutput: LuaTextOutput = LuaTextOutput(),
        ): String {
            LuaToStringGenerator(textOutput).generate(program.statements)
            return textOutput.toString()
        }
    }

    fun generate(statements: List<LuaStatement>) {
        with(textOutput) {
            statements.forEach {
                printLuaStatement(it)
            }
        }
    }

    context(out: LuaTextOutput)
    private inline fun <T> List<T>.printWithSeparator(
        separator: String = ", ",
        action: (T) -> Unit,
    ) {
        for ((index, value) in withIndex()) {
            action(value)
            if (index != lastIndex) {
                out.print(separator)
            }
        }
    }

    context(out: LuaTextOutput)
    private fun List<LuaExpression>.printExpressionsWithSeparator(separator: String = ", ") {
        printWithSeparator(separator) {
            out.printLuaExpression(it)
        }
    }

    private fun LuaTextOutput.printLuaLValue(node: LuaLValue) {
        when (node) {
            is LuaLValue.TableField -> {
                printLuaExpression(node.target)
                print('.')
                print(node.fieldName)
            }

            is LuaLValue.TableIndex -> {
                printLuaExpression(node.target)
                print('[')
                printLuaExpression(node.index)
                print(']')
            }

            is LuaName -> {
                print(node.identifier)
            }
        }
    }

    private fun LuaTextOutput.printFunctionCall(node: LuaExpression.FunctionCall) {
        printLuaExpression(node.target)
        print('(')
        node.args.printExpressionsWithSeparator()
        print(')')
    }

    private fun LuaTextOutput.printMethodCall(node: LuaExpression.MethodCall) {
        printLuaExpression(node.target)
        print(':')
        print(node.methodName)
        print('(')
        node.args.printExpressionsWithSeparator()
        print(')')
    }

    private fun LuaTextOutput.printLuaStatement(node: LuaStatement) {
        when (node) {
            is LuaExpression.FunctionCall -> {
                printFunctionCall(node)
                newline()
            }

            is LuaExpression.MethodCall -> {
                printMethodCall(node)
                newline()
            }

            is LuaStatement.Assignment -> {
                node.targets.printWithSeparator {
                    printLuaLValue(it)
                }
                print(" = ")
                node.values.printExpressionsWithSeparator()
                newline()
            }

            LuaStatement.Break -> {
                println("break")
            }

            is LuaStatement.If -> {
                print("if ")
                printLuaExpression(node.condition)
                println(" then")
                printLuaBlock(node.thenBlock)
                node.elseIfs.forEach { (expr, block) ->
                    print("elseif ")
                    printLuaExpression(expr)
                    println(" then")
                    printLuaBlock(block)
                }
                node.elseBlock?.let { block ->
                    println("else")
                    printLuaBlock(block)
                }
                println("end")
            }

            is LuaStatement.LocalVarDeclaration -> {
                print("local ")
                node.names.printWithSeparator {
                    print(it)
                }
                if (node.initializers.isNotEmpty()) {
                    print(" = ")
                    node.initializers.printExpressionsWithSeparator()
                }
                newline()
            }

            is LuaStatement.RepeatUntil -> {
                println("repeat")
                printLuaBlock(node.body)
                print("until ")
                printLuaExpression(node.condition)
                newline()
            }

            is LuaStatement.Return -> {
                print("return")
                if (node.values.isNotEmpty()) {
                    whitespace()
                    node.values.printExpressionsWithSeparator()
                }
                newline()
            }

            is LuaStatement.While -> {
                print("while ")
                printLuaExpression(node.condition)
                println(" do")
                printLuaBlock(node.body)
                println("end")
            }
        }
    }

    private fun LuaTextOutput.printLuaBlock(node: LuaBlock) {
        withIndent {
            node.statements.forEach { statement ->
                printLuaStatement(statement)
            }
        }
    }

    private fun LuaTextOutput.printLuaField(node: LuaField) {
        when (node) {
            is LuaField.Exp -> {
                printLuaExpression(node.value)
            }

            is LuaField.ExpIndex -> {
                print('[')
                printLuaExpression(node.index)
                print(']')
                print(" = ")
                printLuaExpression(node.value)
            }

            is LuaField.NameIndex -> {
                print(node.index.identifier)
                print(" = ")
                printLuaExpression(node.value)
            }
        }
    }

    private fun LuaTextOutput.printBinaryOperator(operator: LuaBinaryOperator) {
        val str =
            when (operator) {
                LuaBinaryOperator.Add -> "+"
                LuaBinaryOperator.Subtract -> "-"
                LuaBinaryOperator.Multiply -> "*"
                LuaBinaryOperator.Divide -> "/"
                LuaBinaryOperator.Power -> "^"
                LuaBinaryOperator.Modulo -> "%"
                LuaBinaryOperator.Concat -> ".."
                LuaBinaryOperator.Gt -> ">"
                LuaBinaryOperator.Gte -> ">="
                LuaBinaryOperator.Lt -> "<"
                LuaBinaryOperator.Lte -> "<="
                LuaBinaryOperator.Eq -> "=="
                LuaBinaryOperator.Neq -> "~="
                LuaBinaryOperator.And -> "and"
                LuaBinaryOperator.Or -> "or"
            }
        print(str)
    }

    private fun LuaTextOutput.printUnaryOperator(operator: LuaUnaryOperator) {
        when (operator) {
            LuaUnaryOperator.Not -> print("not ")
            LuaUnaryOperator.Length -> print('#')
            LuaUnaryOperator.Negate -> print('-')
        }
    }

    private fun LuaTextOutput.whitespace() {
        print(' ')
    }

    private fun LuaTextOutput.printLuaExpression(node: LuaExpression) {
        when (node) {
            is LuaExpression.UnaryOp -> {
                printUnaryOperator(node.op)
                printLuaExpression(node.operand)
            }

            is LuaExpression.BinaryOp -> {
                print('(')
                printLuaExpression(node.left)
                whitespace()
                printBinaryOperator(node.op)
                whitespace()
                printLuaExpression(node.right)
                print(')')
            }

            is LuaExpression.AnonymousFunction -> {
                print("function (")
                for ((index, param) in node.parameters.withIndex()) {
                    print(param)
                    if (index != node.parameters.lastIndex || node.isVararg) {
                        print(", ")
                    }
                }
                if (node.isVararg) {
                    print("...")
                }
                println(')')
                printLuaBlock(node.block)
                print("end")
            }

            is LuaExpression.BooleanLiteral -> {
                print(node.value.toString())
            }

            is LuaExpression.FunctionCall -> {
                printFunctionCall(node)
            }

            is LuaExpression.MethodCall -> {
                printMethodCall(node)
            }

            LuaExpression.NilLiteral -> {
                print("nil")
            }

            is LuaExpression.DoubleLiteral -> {
                print(node.value.toString())
            }

            is LuaExpression.LongLiteral -> {
                print(node.value.toString())
            }

            is LuaExpression.IntLiteral -> {
                print(node.value.toString())
            }

            is LuaExpression.StringLiteral -> {
                print(LuaStringEscaper.escape(node.value))
            }

            is LuaExpression.TableConstructor -> {
                print('{')
                if (node.fields.isNotEmpty()) {
                    whitespace()
                    node.fields.printWithSeparator("; ") { field ->
                        printLuaField(field)
                    }
                    whitespace()
                }
                print('}')
            }

            LuaExpression.VarargLiteral -> {
                print("...")
            }

            is LuaLValue -> {
                printLuaLValue(node)
            }
        }
    }
}
