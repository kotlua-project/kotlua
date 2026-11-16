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

import io.github.kotlua.ast.*
import io.github.kotlua.parser.generated.Lua51Parser

internal class LuaAstMapperVisitor(
    private val scopeContext: LuaScopeContext,
) : AntlrLuaBaseVisitor<LuaNode?>() {
    override fun visitChunk(ctx: Lua51Parser.ChunkContext): LuaBlock =
        scopeContext.withBlock("<chunk>") { scope ->
            LuaBlock(
                statements = mapChunkStatements(ctx),
                scope = scope,
            )
        }

    override fun visitBlock(ctx: Lua51Parser.BlockContext): LuaBlock =
        scopeContext.withBlock { scope ->
            LuaBlock(
                statements = mapChunkStatements(ctx.chunk()),
                scope = scope,
            )
        }

    private fun mapChunkStatements(ctx: Lua51Parser.ChunkContext): List<LuaStatement> =
        buildList {
            ctx.stat().forEach { statCtx ->
                add(visitNode<LuaStatement>(statCtx))
            }
            ctx.laststat()?.let { lastStatCtx ->
                add(visitNode<LuaStatement>(lastStatCtx))
            }
        }

    override fun visitAssignStat(ctx: Lua51Parser.AssignStatContext): LuaStatement =
        LuaStatement.Assignment(
            targets = visitAll<LuaLValue>(ctx.varlist().var_()),
            values = visitAll<LuaExpression>(ctx.explist().exp()),
        )

    override fun visitFunctionCallStat(ctx: Lua51Parser.FunctionCallStatContext): LuaStatement = visitNode<LuaStatement>(ctx.functioncall())

    override fun visitDoStat(ctx: Lua51Parser.DoStatContext): LuaStatement = visitBlock(ctx.block())

    override fun visitWhileStat(ctx: Lua51Parser.WhileStatContext): LuaStatement {
        val condition = visitNode<LuaExpression>(ctx.exp())
        val body = visitBlock(ctx.block())
        return LuaStatement.While(condition = condition, body = body)
    }

    override fun visitRepeatStat(ctx: Lua51Parser.RepeatStatContext): LuaStatement =
        scopeContext.withBlock("<repeat>") { scope ->
            val body =
                LuaBlock(
                    statements = mapChunkStatements(ctx.block().chunk()),
                    scope = scope,
                )
            val condition = visitNode<LuaExpression>(ctx.exp())
            LuaStatement.RepeatUntil(body = body, condition = condition)
        }

    override fun visitIfStat(ctx: Lua51Parser.IfStatContext): LuaStatement {
        val condition = visitNode<LuaExpression>(ctx.cond!!)
        val thenBlock = visitBlock(ctx.thenBlock!!)
        val elseIfs =
            ctx.elseifCond.zip(ctx.elseifBlock) { condCtx, blockCtx ->
                LuaStatement.If.ElseIf(
                    condition = visitNode<LuaExpression>(condCtx),
                    block = visitBlock(blockCtx),
                )
            }
        val elseBlock = ctx.elseBlock?.let { visitBlock(it) }

        return LuaStatement.If(
            condition = condition,
            thenBlock = thenBlock,
            elseIfs = elseIfs,
            elseBlock = elseBlock,
        )
    }

    override fun visitNumericForStat(ctx: Lua51Parser.NumericForStatContext): LuaStatement {
        val start = visitNode<LuaExpression>(ctx.exp(0)!!)
        val limit = visitNode<LuaExpression>(ctx.exp(1)!!)
        val step = ctx.exp(2)?.let { visitNode<LuaExpression>(it) }

        return scopeContext.withBlock("<numeric for>") { scope ->
            val variable = scopeContext.declareLocal(ctx.NAME().text)
            val body =
                LuaBlock(
                    statements = mapChunkStatements(ctx.block().chunk()),
                    scope = scope,
                )
            LuaStatement.NumericFor(
                variable = variable,
                start = start,
                limit = limit,
                step = step,
                body = body,
            )
        }
    }

    override fun visitGenericForStat(ctx: Lua51Parser.GenericForStatContext): LuaStatement {
        val iterators = visitAll<LuaExpression>(ctx.explist().exp())

        return scopeContext.withBlock("<generic for>") { scope ->
            val variables = ctx.namelist().NAME().map { scopeContext.declareLocal(it.text) }
            val body =
                LuaBlock(
                    statements = mapChunkStatements(ctx.block().chunk()),
                    scope = scope,
                )
            LuaStatement.GenericFor(
                variables = variables,
                iterators = iterators,
                body = body,
            )
        }
    }

    override fun visitFunctionDeclStat(ctx: Lua51Parser.FunctionDeclStatContext): LuaStatement {
        val funcNameCtx = ctx.funcname()
        val allNames = funcNameCtx.NAME().map { it.text }
        val hasMethod = funcNameCtx.text.contains(':')

        val receiverChain = if (hasMethod) allNames.dropLast(1) else allNames
        val methodName = if (hasMethod) allNames.last() else null

        var target: LuaLValue = scopeContext.resolveName(receiverChain.first())
        for (field in receiverChain.drop(1)) {
            target = LuaLValue.TableField(target = target, fieldName = field)
        }

        val fn = mapFuncbody(ctx.funcbody(), hasImplicitSelf = hasMethod)
        return LuaStatement.FunctionDeclaration(
            target = target,
            methodName = methodName,
            function = fn,
        )
    }

    override fun visitLocalFunctionDeclStat(ctx: Lua51Parser.LocalFunctionDeclStatContext): LuaStatement {
        val name = scopeContext.declareLocal(ctx.NAME().text)
        val fn = mapFuncbody(ctx.funcbody(), hasImplicitSelf = false)
        return LuaStatement.LocalFunctionDeclaration(name = name, function = fn)
    }

    override fun visitLocalVarDeclStat(ctx: Lua51Parser.LocalVarDeclStatContext): LuaStatement {
        val initializers =
            ctx
                .explist()
                ?.exp()
                ?.let { visitAll<LuaExpression>(it) }
                .orEmpty()
        val names = ctx.namelist().NAME().map { scopeContext.declareLocal(it.text) }
        return LuaStatement.LocalVarDeclaration(names = names, initializers = initializers)
    }

    override fun visitReturnStat(ctx: Lua51Parser.ReturnStatContext): LuaStatement {
        val values =
            ctx
                .explist()
                ?.exp()
                ?.let { visitAll<LuaExpression>(it) }
                .orEmpty()
        return LuaStatement.Return(values = values)
    }

    override fun visitBreakStat(ctx: Lua51Parser.BreakStatContext): LuaStatement = LuaStatement.Break

    override fun visitNameVar(ctx: Lua51Parser.NameVarContext): LuaLValue = scopeContext.resolveName(ctx.NAME().text)

    override fun visitIndexVar(ctx: Lua51Parser.IndexVarContext): LuaLValue =
        LuaLValue.TableIndex(
            target = visitNode<LuaExpression>(ctx.prefixexp()),
            index = visitNode<LuaExpression>(ctx.exp()),
        )

    override fun visitFieldVar(ctx: Lua51Parser.FieldVarContext): LuaLValue =
        LuaLValue.TableField(
            target = visitNode<LuaExpression>(ctx.prefixexp()),
            fieldName = ctx.NAME().text,
        )

    override fun visitNilExpr(ctx: Lua51Parser.NilExprContext): LuaExpression = LuaExpression.NilLiteral

    override fun visitFalseExpr(ctx: Lua51Parser.FalseExprContext): LuaExpression = LuaExpression.BooleanLiteral(false)

    override fun visitTrueExpr(ctx: Lua51Parser.TrueExprContext): LuaExpression = LuaExpression.BooleanLiteral(true)

    override fun visitVarargExpr(ctx: Lua51Parser.VarargExprContext): LuaExpression = LuaExpression.VarargLiteral

    override fun visitNumberExpr(ctx: Lua51Parser.NumberExprContext): LuaExpression = parseLuaNumber(ctx.NUMBER().text)

    override fun visitStringExpr(ctx: Lua51Parser.StringExprContext): LuaExpression =
        LuaExpression.StringLiteral(parseLuaString(ctx.STRING().text))

    override fun visitFunctionExpr(ctx: Lua51Parser.FunctionExprContext): LuaExpression =
        visitNode<LuaExpression.AnonymousFunction>(ctx.function())

    override fun visitFunction(ctx: Lua51Parser.FunctionContext): LuaExpression.AnonymousFunction =
        mapFuncbody(ctx.funcbody(), hasImplicitSelf = false)

    override fun visitFuncbody(ctx: Lua51Parser.FuncbodyContext): LuaExpression.AnonymousFunction =
        mapFuncbody(ctx, hasImplicitSelf = false)

    private fun mapFuncbody(
        ctx: Lua51Parser.FuncbodyContext,
        hasImplicitSelf: Boolean,
    ): LuaExpression.AnonymousFunction =
        scopeContext.withFunction { fnScope ->
            if (hasImplicitSelf) {
                scopeContext.declareLocal("self")
            }

            val (parameters, isVararg) =
                when (val parlist = ctx.parlist()) {
                    is Lua51Parser.NamedParListContext -> {
                        val params = parlist.namelist().NAME().map { scopeContext.declareLocal(it.text) }
                        params to (parlist.childCount > 1)
                    }

                    is Lua51Parser.VarargParListContext -> {
                        emptyList<LuaName>() to true
                    }

                    null -> {
                        emptyList<LuaName>() to false
                    }

                    else -> {
                        emptyList<LuaName>() to false
                    }
                }

            val body = visitBlock(ctx.block())
            LuaExpression.AnonymousFunction(
                parameters = parameters,
                isVararg = isVararg,
                block = body,
                scope = fnScope,
            )
        }

    override fun visitTableConstructorExpr(ctx: Lua51Parser.TableConstructorExprContext): LuaExpression =
        visitTableconstructor(ctx.tableconstructor())

    override fun visitTableconstructor(ctx: Lua51Parser.TableconstructorContext): LuaExpression.TableConstructor {
        val fields =
            ctx
                .fieldlist()
                ?.field()
                ?.let { visitAll<LuaField>(it) }
                .orEmpty()
        return LuaExpression.TableConstructor(fields = fields)
    }

    override fun visitIndexedField(ctx: Lua51Parser.IndexedFieldContext): LuaField =
        LuaField.Indexed(key = visitNode(ctx.exp(0)!!), value = visitNode(ctx.exp(1)!!))

    override fun visitNamedField(ctx: Lua51Parser.NamedFieldContext): LuaField =
        LuaField.Named(name = ctx.NAME().text, value = visitNode(ctx.exp()))

    override fun visitPositionalField(ctx: Lua51Parser.PositionalFieldContext): LuaField = LuaField.Positional(value = visitNode(ctx.exp()))

    override fun visitUnaryExpr(ctx: Lua51Parser.UnaryExprContext): LuaExpression {
        val op =
            when (val text = ctx.op?.text) {
                "not" -> LuaUnaryOperator.Not
                "#" -> LuaUnaryOperator.Length
                "-" -> LuaUnaryOperator.Negate
                else -> error("Unknown unary operator: $text")
            }
        return LuaExpression.UnaryOp(op = op, operand = visitNode(ctx.exp()))
    }

    override fun visitPowExpr(ctx: Lua51Parser.PowExprContext): LuaExpression = binary(ctx.exp(0)!!, LuaBinaryOperator.Power, ctx.exp(1)!!)

    override fun visitMulDivModExpr(ctx: Lua51Parser.MulDivModExprContext): LuaExpression {
        val op =
            when (val text = ctx.op?.text) {
                "*" -> LuaBinaryOperator.Multiply
                "/" -> LuaBinaryOperator.Divide
                "%" -> LuaBinaryOperator.Modulo
                else -> error("Unknown multiplicative operator: $text")
            }
        return binary(ctx.exp(0)!!, op, ctx.exp(1)!!)
    }

    override fun visitAddSubExpr(ctx: Lua51Parser.AddSubExprContext): LuaExpression {
        val op =
            when (val text = ctx.op?.text) {
                "+" -> LuaBinaryOperator.Add
                "-" -> LuaBinaryOperator.Subtract
                else -> error("Unknown additive operator: $text")
            }
        return binary(ctx.exp(0)!!, op, ctx.exp(1)!!)
    }

    override fun visitConcatExpr(ctx: Lua51Parser.ConcatExprContext): LuaExpression =
        binary(ctx.exp(0)!!, LuaBinaryOperator.Concat, ctx.exp(1)!!)

    override fun visitRelExpr(ctx: Lua51Parser.RelExprContext): LuaExpression {
        val op =
            when (val text = ctx.op?.text) {
                "<" -> LuaBinaryOperator.Lt
                ">" -> LuaBinaryOperator.Gt
                "<=" -> LuaBinaryOperator.Lte
                ">=" -> LuaBinaryOperator.Gte
                "~=" -> LuaBinaryOperator.Neq
                "==" -> LuaBinaryOperator.Eq
                else -> error("Unknown relational operator: $text")
            }
        return binary(ctx.exp(0)!!, op, ctx.exp(1)!!)
    }

    override fun visitAndExpr(ctx: Lua51Parser.AndExprContext): LuaExpression = binary(ctx.exp(0)!!, LuaBinaryOperator.And, ctx.exp(1)!!)

    override fun visitOrExpr(ctx: Lua51Parser.OrExprContext): LuaExpression = binary(ctx.exp(0)!!, LuaBinaryOperator.Or, ctx.exp(1)!!)

    private fun binary(
        left: Lua51Parser.ExpContext,
        op: LuaBinaryOperator,
        right: Lua51Parser.ExpContext,
    ): LuaExpression.BinaryOp =
        LuaExpression.BinaryOp(
            left = visitNode(left),
            op = op,
            right = visitNode(right),
        )

    override fun visitPrefixExpr(ctx: Lua51Parser.PrefixExprContext): LuaExpression = visitNode(ctx.prefixexp())

    override fun visitNamePrefixExp(ctx: Lua51Parser.NamePrefixExpContext): LuaExpression = scopeContext.resolveName(ctx.NAME().text)

    override fun visitParenPrefixExp(ctx: Lua51Parser.ParenPrefixExpContext): LuaExpression = visitNode(ctx.exp())

    override fun visitIndexPrefixExp(ctx: Lua51Parser.IndexPrefixExpContext): LuaExpression =
        LuaLValue.TableIndex(
            target = visitNode(ctx.prefixexp()),
            index = visitNode(ctx.exp()),
        )

    override fun visitFieldPrefixExp(ctx: Lua51Parser.FieldPrefixExpContext): LuaExpression =
        LuaLValue.TableField(
            target = visitNode(ctx.prefixexp()),
            fieldName = ctx.NAME().text,
        )

    override fun visitCallPrefixExp(ctx: Lua51Parser.CallPrefixExpContext): LuaExpression =
        LuaExpression.FunctionCall(
            target = visitNode(ctx.prefixexp()),
            args = extractArgs(ctx.args()),
        )

    override fun visitMethodCallPrefixExp(ctx: Lua51Parser.MethodCallPrefixExpContext): LuaExpression =
        LuaExpression.MethodCall(
            target = visitNode(ctx.prefixexp()),
            methodName = ctx.NAME().text,
            args = extractArgs(ctx.args()),
        )

    override fun visitCallFunctionCall(ctx: Lua51Parser.CallFunctionCallContext): LuaExpression =
        LuaExpression.FunctionCall(
            target = visitNode(ctx.prefixexp()),
            args = extractArgs(ctx.args()),
        )

    override fun visitMethodCallFunctionCall(ctx: Lua51Parser.MethodCallFunctionCallContext): LuaExpression =
        LuaExpression.MethodCall(
            target = visitNode(ctx.prefixexp()),
            methodName = ctx.NAME().text,
            args = extractArgs(ctx.args()),
        )

    private fun extractArgs(ctx: Lua51Parser.ArgsContext): List<LuaExpression> =
        when (ctx) {
            is Lua51Parser.ParenArgsContext -> {
                ctx
                    .explist()
                    ?.exp()
                    ?.let { visitAll<LuaExpression>(it) }
                    .orEmpty()
            }

            is Lua51Parser.TableArgsContext -> {
                listOf(visitTableconstructor(ctx.tableconstructor()))
            }

            is Lua51Parser.StringArgsContext -> {
                listOf(LuaExpression.StringLiteral(parseLuaString(ctx.STRING().text)))
            }

            else -> {
                error("Unsupported args context: $ctx")
            }
        }

    private fun parseLuaNumber(text: String): LuaExpression {
        if (text.startsWith("0x", ignoreCase = true)) {
            val hex = text.substring(2)
            hex.toIntOrNull(16)?.let { return LuaExpression.IntLiteral(it) }
            hex.toLongOrNull(16)?.let { return LuaExpression.LongLiteral(it) }
            val asDouble = hex.fold(0.0) { acc, c -> acc * 16.0 + c.digitToInt(16) }
            return LuaExpression.DoubleLiteral(asDouble)
        }
        if ('.' !in text && 'e' !in text && 'E' !in text) {
            text.toIntOrNull()?.let { return LuaExpression.IntLiteral(it) }
            text.toLongOrNull()?.let { return LuaExpression.LongLiteral(it) }
        }
        return LuaExpression.DoubleLiteral(text.toDouble())
    }

    private fun parseLuaString(raw: String): String {
        if (raw.startsWith("[")) {
            val openBracketEnd = raw.indexOf('[', startIndex = 1)
            val eqCount = openBracketEnd - 1
            val content = raw.substring(openBracketEnd + 1, raw.length - (eqCount + 2))
            return when {
                content.startsWith("\r\n") || content.startsWith("\n\r") -> content.substring(2)
                content.startsWith("\n") || content.startsWith("\r") -> content.substring(1)
                else -> content
            }
        }

        val inner = raw.substring(1, raw.length - 1)
        val result = StringBuilder(inner.length)
        var i = 0
        while (i < inner.length) {
            val ch = inner[i]
            if (ch != '\\') {
                result.append(ch)
                i++
                continue
            }
            i++
            if (i >= inner.length) break
            when (val esc = inner[i]) {
                'a' -> {
                    result.append('\u0007')
                    i++
                }

                'b' -> {
                    result.append('\b')
                    i++
                }

                'f' -> {
                    result.append('\u000C')
                    i++
                }

                'n' -> {
                    result.append('\n')
                    i++
                }

                'r' -> {
                    result.append('\r')
                    i++
                }

                't' -> {
                    result.append('\t')
                    i++
                }

                'v' -> {
                    result.append('\u000B')
                    i++
                }

                '\\', '"', '\'' -> {
                    result.append(esc)
                    i++
                }

                '\r' -> {
                    result.append('\n')
                    i += if (i + 1 < inner.length && inner[i + 1] == '\n') 2 else 1
                }

                '\n' -> {
                    result.append('\n')
                    i += if (i + 1 < inner.length && inner[i + 1] == '\r') 2 else 1
                }

                in '0'..'9' -> {
                    var code = 0
                    var digits = 0
                    while (digits < 3 && i < inner.length && inner[i] in '0'..'9') {
                        code = code * 10 + (inner[i] - '0')
                        i++
                        digits++
                    }
                    require(code <= 255) { "Decimal escape sequence out of range: \\$code" }
                    result.append(Char(code))
                }

                else -> {
                    error("Invalid escape sequence: \\$esc")
                }
            }
        }
        return result.toString()
    }
}
