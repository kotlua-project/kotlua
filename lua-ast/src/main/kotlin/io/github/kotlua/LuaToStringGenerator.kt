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
import io.github.kotlua.ast.LuaCompositeBlock
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaField
import io.github.kotlua.ast.LuaLValue
import io.github.kotlua.ast.LuaName
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.LuaUnaryOperator

class LuaToStringGenerator private constructor(
    val textOutput: LuaTextOutput,
) {
    private var previousStatementEndedWithExpression = false

    companion object {
        fun generate(
            program: LuaBlock,
            textOutput: LuaTextOutput = LuaTextOutput(),
        ): String {
            LuaToStringGenerator(textOutput).generate(program.statements)
            return textOutput.toString()
        }

        fun generateStatement(
            statement: LuaStatement,
            textOutput: LuaTextOutput = LuaTextOutput(),
        ): String {
            with(LuaToStringGenerator(textOutput)) {
                textOutput.printLuaStatement(statement)
            }
            return textOutput.toString()
        }

        fun generateExpression(
            expression: LuaExpression,
            textOutput: LuaTextOutput = LuaTextOutput(),
        ): String {
            with(LuaToStringGenerator(textOutput)) {
                textOutput.printLuaExpression(expression)
            }
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
                out.printToOutput(separator)
            }
        }
    }

    context(out: LuaTextOutput)
    private fun List<LuaExpression>.printExpressionsWithSeparator(separator: String = ", ") {
        printWithSeparator(separator) {
            out.printLuaExpression(it)
        }
    }

    private fun LuaTextOutput.printPrefixExpression(node: LuaExpression) {
        val isPrefixExp =
            node is LuaLValue ||
                node is LuaExpression.FunctionCall ||
                node is LuaExpression.MethodCall

        if (isPrefixExp) {
            printLuaExpression(node)
        } else {
            printToOutput('(')
            printLuaExpression(node)
            printToOutput(')')
        }
    }

    private fun LuaTextOutput.printLuaLValue(node: LuaLValue) {
        when (node) {
            is LuaLValue.TableField -> {
                printPrefixExpression(node.target)
                printToOutput('.')
                printToOutput(node.fieldName)
            }

            is LuaLValue.TableIndex -> {
                printPrefixExpression(node.target)
                printToOutput('[')
                printLuaExpression(node.index)
                printToOutput(']')
            }

            is LuaName -> {
                printToOutput(node.identifier)
            }
        }
    }

    private fun LuaTextOutput.printFunctionCall(node: LuaExpression.FunctionCall) {
        printPrefixExpression(node.target)
        printToOutput('(')
        node.args.printExpressionsWithSeparator()
        printToOutput(')')
    }

    private fun LuaTextOutput.printMethodCall(node: LuaExpression.MethodCall) {
        printPrefixExpression(node.target)
        printToOutput(':')
        printToOutput(node.methodName)
        printToOutput('(')
        node.args.printExpressionsWithSeparator()
        printToOutput(')')
    }

    private fun LuaTextOutput.printLuaStatement(node: LuaStatement) {
        printWithLeadingSemi(node) {
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
                    printToOutput(" = ")
                    node.values.printExpressionsWithSeparator()
                    newline()
                }

                LuaStatement.Break -> {
                    printWithNewLine("break")
                }

                is LuaStatement.If -> {
                    printToOutput("if ")
                    printLuaExpression(node.condition)
                    printWithNewLine(" then")
                    printLuaBlock(node.thenBlock)
                    node.elseIfs.forEach { (expr, block) ->
                        printToOutput("elseif ")
                        printLuaExpression(expr)
                        printWithNewLine(" then")
                        printLuaBlock(block)
                    }
                    node.elseBlock?.let { block ->
                        printWithNewLine("else")
                        printLuaBlock(block)
                    }
                    printWithNewLine("end")
                }

                is LuaStatement.LocalVarDeclaration -> {
                    printToOutput("local ")
                    node.names.printWithSeparator {
                        printToOutput(it.identifier)
                    }
                    if (node.initializers.isNotEmpty()) {
                        printToOutput(" = ")
                        node.initializers.printExpressionsWithSeparator()
                    }
                    newline()
                }

                is LuaStatement.RepeatUntil -> {
                    printWithNewLine("repeat")
                    printLuaBlock(node.body)
                    printToOutput("until ")
                    printLuaExpression(node.condition)
                    newline()
                }

                is LuaStatement.Return -> {
                    printToOutput("return")
                    if (node.values.isNotEmpty()) {
                        whitespace()
                        node.values.printExpressionsWithSeparator()
                    }
                    newline()
                }

                is LuaStatement.While -> {
                    printToOutput("while ")
                    printLuaExpression(node.condition)
                    printWithNewLine(" do")
                    printLuaBlock(node.body)
                    printWithNewLine("end")
                }

                is LuaStatement.LocalFunctionDeclaration -> {
                    printToOutput("local function ")
                    printToOutput(node.name.identifier)
                    printToOutput('(')
                    printLuaAnonymousFunctionParams(node.function)
                    printWithNewLine(')')
                    printLuaBlock(node.function.block)
                    printWithNewLine("end")
                }

                is LuaStatement.FunctionDeclaration -> {
                    printToOutput("function ")
                    printLuaLValue(node.target)
                    node.methodName?.let {
                        printToOutput(':')
                        printToOutput(it)
                    }
                    printToOutput('(')
                    printLuaAnonymousFunctionParams(node.function)
                    printWithNewLine(')')
                    printLuaBlock(node.function.block)
                    printWithNewLine("end")
                }

                is LuaStatement.NumericFor -> {
                    printToOutput("for ")
                    printToOutput(node.variable.identifier)
                    printToOutput(" = ")
                    printLuaExpression(node.start)
                    printToOutput(", ")
                    printLuaExpression(node.limit)
                    node.step?.let {
                        printToOutput(", ")
                        printLuaExpression(it)
                    }
                    printWithNewLine(" do")
                    printLuaBlock(node.body)
                    printWithNewLine("end")
                }

                is LuaStatement.GenericFor -> {
                    printToOutput("for ")
                    node.variables.printWithSeparator {
                        printToOutput(it.identifier)
                    }
                    printToOutput(" in ")
                    node.iterators.printExpressionsWithSeparator()
                    printWithNewLine(" do")
                    printLuaBlock(node.body)
                    printWithNewLine("end")
                }

                is LuaBlock -> {
                    printWithNewLine("do")
                    printLuaBlock(node)
                    printWithNewLine("end")
                }

                is LuaCompositeBlock -> {
                    printStatements(node.statements)
                }

                LuaStatement.Empty -> {
                    Unit
                }
            }
        }
    }

    private fun LuaTextOutput.printWithLeadingSemi(
        node: LuaStatement,
        block: LuaTextOutput.() -> Unit,
    ) {
        val preserveSemi = node !is LuaCompositeBlock && node !== LuaStatement.Empty
        if (preserveSemi && previousStatementEndedWithExpression && node.startsWithOpenParen()) {
            printToOutput(';')
        }
        block()
        if (preserveSemi) {
            previousStatementEndedWithExpression = node.endsWithExpression()
        }
    }

    private fun LuaTextOutput.printLuaBlock(node: LuaBlock) {
        val savedFlag = previousStatementEndedWithExpression
        previousStatementEndedWithExpression = false
        withIndent {
            printStatements(node.statements)
        }
        previousStatementEndedWithExpression = savedFlag
    }

    private fun LuaTextOutput.printStatements(statements: Iterable<LuaStatement>) {
        statements.forEach { statement ->
            printLuaStatement(statement)
        }
    }

    private fun LuaTextOutput.printLuaField(node: LuaField) {
        when (node) {
            is LuaField.Positional -> {
                printLuaExpression(node.value)
            }

            is LuaField.Indexed -> {
                printToOutput('[')
                printLuaExpression(node.key)
                printToOutput(']')
                printToOutput(" = ")
                printLuaExpression(node.value)
            }

            is LuaField.Named -> {
                printToOutput(node.name)
                printToOutput(" = ")
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
        printToOutput(str)
    }

    private fun LuaTextOutput.printUnaryOperator(operator: LuaUnaryOperator) {
        when (operator) {
            LuaUnaryOperator.Not -> printToOutput("not ")
            LuaUnaryOperator.Length -> printToOutput('#')
            LuaUnaryOperator.Negate -> printToOutput('-')
        }
    }

    private fun LuaTextOutput.whitespace() {
        printToOutput(' ')
    }

    private fun LuaTextOutput.printLuaAnonymousFunctionParams(node: LuaExpression.AnonymousFunction) {
        for ((index, param) in node.parameters.withIndex()) {
            printToOutput(param.identifier)
            if (index != node.parameters.lastIndex || node.isVararg) {
                printToOutput(", ")
            }
        }
        if (node.isVararg) {
            printToOutput("...")
        }
    }

    private fun LuaTextOutput.printLuaExpression(node: LuaExpression) {
        when (node) {
            is LuaExpression.UnaryOp -> {
                printUnaryOperator(node.op)
                val operandPrecedence = node.operand.precedence()
                val needParens =
                    operandPrecedence < LuaBinaryOperator.UNARY_PRECEDENCE ||
                        (node.op == LuaUnaryOperator.Negate && node.operand.startsWithMinus())
                printWithParentheses(needParens = needParens) {
                    printLuaExpression(node.operand)
                }
            }

            is LuaExpression.BinaryOp -> {
                val binOpPrecedence = node.op.precedence
                val rightAssociative = node.op.isRightAssociative

                val leftPrecedence = node.left.precedence()
                val rightPrecedence = node.right.precedence()

                val wrapLeft =
                    when {
                        rightAssociative -> leftPrecedence <= binOpPrecedence
                        else -> leftPrecedence < binOpPrecedence
                    }
                val wrapRight =
                    when {
                        rightAssociative -> rightPrecedence < binOpPrecedence
                        else -> rightPrecedence <= binOpPrecedence
                    }

                printWithParentheses(needParens = wrapLeft) {
                    printLuaExpression(node.left)
                }
                whitespace()
                printBinaryOperator(node.op)
                whitespace()
                printWithParentheses(needParens = wrapRight) {
                    printLuaExpression(node.right)
                }
            }

            is LuaExpression.AnonymousFunction -> {
                printToOutput("function(")
                printLuaAnonymousFunctionParams(node)
                printWithNewLine(')')
                printLuaBlock(node.block)
                printToOutput("end")
            }

            is LuaExpression.BooleanLiteral -> {
                printToOutput(node.value.toString())
            }

            is LuaExpression.FunctionCall -> {
                printFunctionCall(node)
            }

            is LuaExpression.MethodCall -> {
                printMethodCall(node)
            }

            LuaExpression.NilLiteral -> {
                printToOutput("nil")
            }

            is LuaExpression.DoubleLiteral -> {
                when {
                    node.value.isNaN() -> printToOutput("0 / 0")
                    node.value == Double.POSITIVE_INFINITY -> printToOutput("1 / 0")
                    node.value == Double.NEGATIVE_INFINITY -> printToOutput("-1 / 0")
                    else -> printToOutput(node.value.toString())
                }
            }

            is LuaExpression.LongLiteral -> {
                printToOutput(node.value.toString())
            }

            is LuaExpression.IntLiteral -> {
                printToOutput(node.value.toString())
            }

            is LuaExpression.StringLiteral -> {
                printToOutput(LuaStringEscaper.escape(node.value))
            }

            is LuaExpression.TableConstructor -> {
                printToOutput('{')
                if (node.fields.isNotEmpty()) {
                    whitespace()
                    node.fields.printWithSeparator("; ") { field ->
                        printLuaField(field)
                    }
                    whitespace()
                }
                printToOutput('}')
            }

            LuaExpression.VarargLiteral -> {
                printToOutput("...")
            }

            is LuaLValue -> {
                printLuaLValue(node)
            }
        }
    }

    private fun LuaExpression.precedence(): Int =
        when (this) {
            is LuaExpression.BinaryOp -> op.precedence
            is LuaExpression.UnaryOp -> LuaBinaryOperator.UNARY_PRECEDENCE
            is LuaExpression.IntLiteral if value < 0 -> LuaBinaryOperator.UNARY_PRECEDENCE
            is LuaExpression.LongLiteral if value < 0L -> LuaBinaryOperator.UNARY_PRECEDENCE
            is LuaExpression.DoubleLiteral if (value.isNaN() || value.isInfinite()) -> LuaBinaryOperator.Divide.precedence
            is LuaExpression.DoubleLiteral if value.hasNegativeSign() -> LuaBinaryOperator.UNARY_PRECEDENCE
            else -> LuaBinaryOperator.ATOM_PRECEDENCE
        }

    private fun LuaExpression.startsWithMinus(): Boolean =
        when (this) {
            is LuaExpression.UnaryOp -> op == LuaUnaryOperator.Negate
            is LuaExpression.IntLiteral -> value < 0
            is LuaExpression.LongLiteral -> value < 0L
            is LuaExpression.DoubleLiteral -> !value.isNaN() && value.hasNegativeSign()
            else -> false
        }

    private fun Double.hasNegativeSign(): Boolean = toRawBits() < 0L

    private inline fun LuaTextOutput.printWithParentheses(
        needParens: Boolean,
        block: LuaTextOutput.() -> Unit,
    ) {
        if (needParens) printToOutput('(')
        block()
        if (needParens) printToOutput(')')
    }

    private fun LuaExpression.startsWithOpenParenAsPrefix(): Boolean =
        when (this) {
            is LuaName -> false
            is LuaLValue.TableField -> target.startsWithOpenParenAsPrefix()
            is LuaLValue.TableIndex -> target.startsWithOpenParenAsPrefix()
            is LuaExpression.FunctionCall -> target.startsWithOpenParenAsPrefix()
            is LuaExpression.MethodCall -> target.startsWithOpenParenAsPrefix()
            else -> true
        }

    private fun LuaStatement.startsWithOpenParen(): Boolean =
        when (this) {
            is LuaExpression.FunctionCall -> target.startsWithOpenParenAsPrefix()
            is LuaExpression.MethodCall -> target.startsWithOpenParenAsPrefix()
            is LuaStatement.Assignment -> targets.firstOrNull()?.startsWithOpenParenAsPrefix() == true
            else -> false
        }

    private fun LuaStatement.endsWithExpression(): Boolean =
        when (this) {
            is LuaStatement.Assignment,
            is LuaExpression.FunctionCall,
            is LuaExpression.MethodCall,
            is LuaStatement.RepeatUntil,
            -> true

            is LuaStatement.LocalVarDeclaration -> initializers.isNotEmpty()

            else -> false
        }
}
