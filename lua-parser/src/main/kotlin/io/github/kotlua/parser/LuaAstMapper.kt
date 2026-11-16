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

import io.github.kotlua.ast.LuaBlock
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.scope.LuaRootScope
import io.github.kotlua.ast.scope.LuaScope
import io.github.kotlua.parser.generated.Lua51Lexer
import io.github.kotlua.parser.generated.Lua51Parser
import org.antlr.v4.kotlinruntime.BaseErrorListener
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.RecognitionException
import org.antlr.v4.kotlinruntime.Recognizer

class LuaAstMapper(
    scope: LuaScope = LuaRootScope(),
) {
    private val scopeContext = LuaScopeContext(scope)

    fun mapCode(code: String): List<LuaStatement> {
        val errorListener =
            object : BaseErrorListener() {
                override fun syntaxError(
                    recognizer: Recognizer<*, *>,
                    offendingSymbol: Any?,
                    line: Int,
                    charPositionInLine: Int,
                    msg: String,
                    e: RecognitionException?,
                ) {
                    error("Lua syntax error at $line:$charPositionInLine: $msg")
                }
            }

        val lexer =
            Lua51Lexer(CharStreams.fromString(code)).apply {
                removeErrorListeners()
                addErrorListener(errorListener)
            }
        val parser =
            Lua51Parser(CommonTokenStream(lexer)).apply {
                removeErrorListeners()
                addErrorListener(errorListener)
            }

        return when (val tree = parser.expressionOrStatements()) {
            is Lua51Parser.SingleExpInputContext -> {
                val expr = mapExpression(tree.exp())
                listOf(LuaStatement.Return(listOf(expr)))
            }

            is Lua51Parser.StatementsInputContext -> {
                mapChunk(tree.chunk()).statements
            }

            else -> {
                emptyList()
            }
        }
    }

    fun mapChunk(chunk: Lua51Parser.ChunkContext): LuaBlock = LuaAstMapperVisitor(scopeContext).visitChunk(chunk)

    fun mapStatement(statement: Lua51Parser.StatContext): LuaStatement =
        LuaAstMapperVisitor(scopeContext).visit(statement) as? LuaStatement
            ?: error("Expecting a statement")

    fun mapExpression(expression: Lua51Parser.ExpContext): LuaExpression =
        LuaAstMapperVisitor(scopeContext).visit(expression) as? LuaExpression
            ?: error("Expecting an expression")
}
