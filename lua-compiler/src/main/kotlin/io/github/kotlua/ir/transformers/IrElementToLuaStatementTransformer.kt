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
package io.github.kotlua.ir.transformers

import io.github.kotlua.ast.LuaBinaryOperator
import io.github.kotlua.ast.LuaBlock
import io.github.kotlua.ast.LuaCompositeBlock
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaLValue
import io.github.kotlua.ast.LuaName
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.LuaUnaryOperator
import io.github.kotlua.ast.scope.LuaLocalScope
import io.github.kotlua.ir.lower.LuaMultiReturnLowering
import io.github.kotlua.ir.utils.LuaGenerationContext
import io.github.kotlua.ir.utils.buildLuaIf
import io.github.kotlua.ir.utils.luaElementAccess
import io.github.kotlua.ir.utils.resolveExternalPropertyTarget
import io.github.kotlua.ir.utils.resolveFieldAccess
import io.github.kotlua.ir.utils.translateCall
import io.github.kotlua.ir.utils.translateExternalArguments
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.realOverrideTarget
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrReturnableBlockSymbol
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.irError
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.ir.util.isElseBranch
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.ir.util.parentAsClass

class IrElementToLuaStatementTransformer : BaseIrElementToLuaNodeTransformer<LuaStatement, LuaGenerationContext>() {
    private val exprTransformer = IrElementToLuaExpressionTransformer()

    override fun visitFunction(
        declaration: IrFunction,
        data: LuaGenerationContext,
    ): LuaStatement {
        irError("All functions must be already lowered") {
            withIrEntry("declaration", declaration)
        }
    }

    override fun visitBlockBody(
        body: IrBlockBody,
        data: LuaGenerationContext,
    ): LuaStatement =
        LuaBlock(
            statements = unwrapSingleBlock(translateAndNormalizeStatements(body.statements, data)),
            scope = data.currentScope,
        )

    override fun visitBlock(
        expression: IrBlock,
        data: LuaGenerationContext,
    ): LuaStatement {
        val blockScope = LuaLocalScope(data.currentScope)
        val blockContext = LuaGenerationContext(data.staticContext, blockScope, data.localNames)
        val normalized = unwrapSingleBlock(translateAndNormalizeStatements(expression.statements, blockContext)).toMutableList()
        if (expression is IrReturnableBlock) {
            stripTailBreaks(normalized)
            if (normalized.isEmpty()) {
                return LuaStatement.Empty
            }
            if (containsBreakForCurrentLoop(normalized)) {
                return LuaStatement.RepeatUntil(
                    body = LuaBlock(statements = normalized, scope = blockScope),
                    condition = LuaExpression.BooleanLiteral(true),
                )
            }
        }
        if (normalized.isEmpty()) {
            return LuaStatement.Empty
        }
        if (!containsDirectLocalDeclaration(normalized)) {
            return if (normalized.size == 1) normalized.single() else LuaCompositeBlock(normalized)
        }
        return LuaBlock(statements = normalized, scope = blockScope)
    }

    private fun containsBreakForCurrentLoop(statements: List<LuaStatement>): Boolean {
        for (stmt in statements) {
            val found =
                when (stmt) {
                    is LuaStatement.Break -> {
                        true
                    }

                    is LuaBlock -> {
                        containsBreakForCurrentLoop(stmt.statements)
                    }

                    is LuaCompositeBlock -> {
                        containsBreakForCurrentLoop(stmt.statements)
                    }

                    is LuaStatement.If -> {
                        containsBreakForCurrentLoop(stmt.thenBlock.statements) ||
                            stmt.elseIfs.any { containsBreakForCurrentLoop(it.block.statements) } ||
                            (stmt.elseBlock != null && containsBreakForCurrentLoop(stmt.elseBlock?.statements.orEmpty()))
                    }

                    else -> {
                        false
                    }
                }
            if (found) return true
        }
        return false
    }

    override fun visitWhileLoop(
        loop: IrWhileLoop,
        data: LuaGenerationContext,
    ): LuaStatement {
        val condition = loop.condition.accept(exprTransformer, data)
        val body = translateLoopBody(loop.body, data)
        return LuaStatement.While(
            condition = condition,
            body = body,
        )
    }

    override fun visitDoWhileLoop(
        loop: IrDoWhileLoop,
        data: LuaGenerationContext,
    ): LuaStatement {
        val body = translateLoopBody(loop.body, data)
        val luaCondition = loop.condition.accept(exprTransformer, data)
        val untilCondition =
            when (luaCondition) {
                is LuaExpression.BooleanLiteral -> LuaExpression.BooleanLiteral(!luaCondition.value)
                else -> LuaExpression.UnaryOp(LuaUnaryOperator.Not, luaCondition)
            }
        return LuaStatement.RepeatUntil(
            body = body,
            condition = untilCondition,
        )
    }

    override fun visitComposite(
        expression: IrComposite,
        data: LuaGenerationContext,
    ): LuaStatement {
        if (expression.origin == LuaMultiReturnLowering.LUA_MULTI_ASSIGN_ORIGIN) {
            val call = expression.statements.last() as IrExpression
            val names =
                expression.statements.dropLast(1).map { stmt ->
                    when (stmt) {
                        is IrVariable -> data.getNameForValueDeclaration(stmt)
                        else -> LuaName("_")
                    }
                }
            return LuaStatement.LocalVarDeclaration(
                names = names,
                initializers = listOf(call.accept(exprTransformer, data)),
            )
        }

        val statements =
            expression.statements
                .map { it.accept(this, data) }
                .filter { it !is LuaStatement.Empty }

        return when (statements.size) {
            0 -> LuaStatement.Empty
            1 -> statements.single()
            else -> LuaCompositeBlock(statements)
        }
    }

    override fun visitExpression(
        expression: IrExpression,
        data: LuaGenerationContext,
    ): LuaStatement =
        when (val luaExpr = expression.accept(exprTransformer, data)) {
            is LuaStatement -> {
                luaExpr
            }

            is LuaExpression.NilLiteral,
            is LuaExpression.BooleanLiteral,
            is LuaExpression.IntLiteral,
            is LuaExpression.DoubleLiteral,
            is LuaExpression.LongLiteral,
            is LuaExpression.StringLiteral,
            is LuaName,
            -> {
                LuaStatement.Empty
            }

            else -> {
                LuaStatement.LocalVarDeclaration(
                    names = listOf(LuaName("_")),
                    initializers = listOf(luaExpr),
                )
            }
        }

    override fun visitVariable(
        declaration: IrVariable,
        data: LuaGenerationContext,
    ): LuaStatement {
        val varName = data.getNameForValueDeclaration(declaration)
        val init = declaration.initializer?.accept(exprTransformer, data)
        return LuaStatement.LocalVarDeclaration(
            names = listOf(varName),
            initializers = listOfNotNull(init),
        )
    }

    override fun visitSetValue(
        expression: IrSetValue,
        data: LuaGenerationContext,
    ): LuaStatement =
        LuaStatement.Assignment(
            targets = listOf(data.getNameForValueDeclaration(expression.symbol.owner)),
            values = listOf(expression.value.accept(exprTransformer, data)),
        )

    override fun visitReturn(
        expression: IrReturn,
        data: LuaGenerationContext,
    ): LuaStatement {
        if (expression.returnTargetSymbol is IrReturnableBlockSymbol) {
            return LuaStatement.Break
        }
        if (expression.value is IrGetObjectValue && expression.value.type.isUnit()) {
            val values = if (data.isConstructor) listOfNotNull(data.currentThisName) else emptyList()
            return LuaStatement.Return(values)
        }
        return LuaStatement.Return(
            values = listOf(expression.value.accept(exprTransformer, data)),
        )
    }

    override fun visitBreak(
        jump: IrBreak,
        data: LuaGenerationContext,
    ): LuaStatement = LuaStatement.Break

    override fun visitContinue(
        jump: IrContinue,
        data: LuaGenerationContext,
    ): LuaStatement {
        irError("All IrContinue nodes must be lowered by LuaLoopLowering") { withIrEntry("operation", jump) }
    }

    override fun visitCall(
        expression: IrCall,
        data: LuaGenerationContext,
    ): LuaStatement {
        if (data.checkIfLuaCode(expression.symbol) || data.checkIfHasAssociatedLuaCode(expression.symbol)) {
            return LuaCallTransformer(expression, data).generateStatement()
        }
        data.staticContext.statementIntrinsics[expression.symbol]?.let {
            return it(expression, data)
        }
        val targetFn = expression.symbol.owner.realOverrideTarget
        if (targetFn.isEffectivelyExternal()) {
            val property = targetFn.correspondingPropertySymbol?.owner
            if (property != null && targetFn == property.setter) {
                val target = resolveExternalPropertyTarget(expression, targetFn, property, data, exprTransformer)
                val args = translateExternalArguments(expression, targetFn, data, exprTransformer)
                return LuaStatement.Assignment(targets = listOf(target), values = listOf(args.single()))
            }
        }
        return when (val result = translateCall(expression, data, exprTransformer)) {
            is LuaStatement -> {
                result
            }

            else -> {
                LuaStatement.LocalVarDeclaration(
                    names = listOf(LuaName("_")),
                    initializers = listOf(result),
                )
            }
        }
    }

    override fun visitWhen(
        expression: IrWhen,
        data: LuaGenerationContext,
    ): LuaStatement {
        val branches = expression.branches
        if (branches.isEmpty()) return LuaStatement.Empty

        val single = branches.singleOrNull()
        if (single != null && isElseBranch(single)) {
            return single.result.accept(this, data)
        }

        val luaIf =
            buildLuaIf(expression, data, data.currentScope) { branchResult, parentScope ->
                val blockScope = LuaLocalScope(parentScope)
                val branchContext = LuaGenerationContext(data.staticContext, blockScope, data.localNames)
                val irStmts =
                    when (branchResult) {
                        is IrContainerExpression -> branchResult.statements
                        else -> listOf(branchResult)
                    }
                LuaBlock(unwrapSingleBlock(translateAndNormalizeStatements(irStmts, branchContext)), blockScope)
            }

        if (luaIf.elseIfs.isEmpty()) {
            val thenEmpty = luaIf.thenBlock.statements.isEmpty()
            val elseBlock = luaIf.elseBlock
            val elseEmpty = elseBlock == null || elseBlock.statements.isEmpty()

            if (thenEmpty && elseEmpty) {
                return if (isPureLuaExpression(luaIf.condition)) {
                    LuaStatement.Empty
                } else {
                    LuaStatement.LocalVarDeclaration(names = listOf(LuaName("_")), initializers = listOf(luaIf.condition))
                }
            }
            if (!thenEmpty && elseBlock != null && elseEmpty) {
                return LuaStatement.If(
                    condition = luaIf.condition,
                    thenBlock = luaIf.thenBlock,
                    elseIfs = emptyList(),
                    elseBlock = null,
                )
            }
            if (thenEmpty && elseBlock != null) {
                return LuaStatement.If(
                    condition = negateCondition(luaIf.condition),
                    thenBlock = elseBlock,
                    elseIfs = emptyList(),
                    elseBlock = null,
                )
            }
        }

        return luaIf
    }

    override fun visitTypeOperator(
        expression: IrTypeOperatorCall,
        data: LuaGenerationContext,
    ): LuaStatement =
        when (expression.operator) {
            IrTypeOperator.IMPLICIT_COERCION_TO_UNIT -> expression.argument.accept(this, data)
            else -> super.visitTypeOperator(expression, data)
        }

    override fun visitSetField(
        expression: IrSetField,
        data: LuaGenerationContext,
    ): LuaStatement {
        val dest = resolveFieldAccess(expression, data, exprTransformer)
        val source = expression.value.accept(exprTransformer, data)
        return LuaStatement.Assignment(
            targets = listOf(dest),
            values = listOf(source),
        )
    }

    override fun visitDynamicOperatorExpression(
        expression: IrDynamicOperatorExpression,
        data: LuaGenerationContext,
    ): LuaStatement {
        if (expression.operator == IrDynamicOperator.EQ) {
            val dest = expression.left.accept(exprTransformer, data) as LuaLValue
            val source = expression.right.accept(exprTransformer, data)
            return LuaStatement.Assignment(targets = listOf(dest), values = listOf(source))
        }
        return super.visitDynamicOperatorExpression(expression, data)
    }

    override fun visitDelegatingConstructorCall(
        expression: IrDelegatingConstructorCall,
        data: LuaGenerationContext,
    ): LuaStatement {
        val ctor = expression.symbol.owner
        if (ctor.constructedClass.symbol == data.staticContext.backendContext.irBuiltIns.anyClass) {
            return LuaStatement.Empty
        }
        val thisName =
            data.currentThisName
                ?: compilationException("Delegating constructor call outside constructor", expression)
        val classRef = data.getRefForStaticDeclaration(ctor.parentAsClass)
        val ctorName = data.staticContext.irNamer.getNameForConstructor(ctor)
        val args =
            listOf(thisName) +
                ctor.nonDispatchParameters.map { param ->
                    expression.arguments[param.indexInParameters]
                        ?.accept(exprTransformer, data)
                        ?: LuaExpression.NilLiteral
                }
        return LuaExpression.FunctionCall(
            target = luaElementAccess(ctorName, classRef),
            args = args,
        )
    }

    override fun visitInstanceInitializerCall(
        expression: IrInstanceInitializerCall,
        data: LuaGenerationContext,
    ): LuaStatement = LuaStatement.Empty

    private fun normalizeBlockStatements(statements: List<LuaStatement>): List<LuaStatement> {
        val result = mutableListOf<LuaStatement>()
        for (stmt in statements) {
            when (stmt) {
                LuaStatement.Empty -> {
                    continue
                }

                is LuaCompositeBlock -> {
                    val normalizedInner = normalizeBlockStatements(stmt.statements)
                    if (normalizedInner.isEmpty()) continue
                    val terminated = normalizedInner.last().isBlockTerminator()
                    result +=
                        if (normalizedInner.size == 1) {
                            normalizedInner.single()
                        } else {
                            LuaCompositeBlock(normalizedInner)
                        }
                    if (terminated) break
                }

                else -> {
                    result += stmt
                    if (stmt.isBlockTerminator()) break
                }
            }
        }
        return result
    }

    private fun LuaStatement.isBlockTerminator(): Boolean = this is LuaStatement.Break || this is LuaStatement.Return

    private fun isPureLuaExpression(expr: LuaExpression): Boolean =
        when (expr) {
            is LuaExpression.NilLiteral,
            is LuaExpression.BooleanLiteral,
            is LuaExpression.IntLiteral,
            is LuaExpression.DoubleLiteral,
            is LuaExpression.LongLiteral,
            is LuaExpression.StringLiteral,
            is LuaName,
            -> true

            is LuaExpression.UnaryOp -> isPureLuaExpression(expr.operand)

            is LuaExpression.BinaryOp -> isPureLuaExpression(expr.left) && isPureLuaExpression(expr.right)

            else -> false
        }

    private fun translateLoopBody(
        body: IrExpression?,
        data: LuaGenerationContext,
    ): LuaBlock {
        val bodyScope = LuaLocalScope(data.currentScope)
        val bodyContext = LuaGenerationContext(data.staticContext, bodyScope, data.localNames)
        val irStmts =
            when (body) {
                null -> emptyList()
                is IrBlock -> body.statements
                else -> listOf(body)
            }
        return LuaBlock(
            statements = unwrapSingleBlock(translateAndNormalizeStatements(irStmts, bodyContext)),
            scope = bodyScope,
        )
    }

    private fun translateAndNormalizeStatements(
        irStatements: List<IrStatement>,
        data: LuaGenerationContext,
    ): List<LuaStatement> {
        val translated = mutableListOf<LuaStatement>()
        for (irStmt in irStatements) {
            translated += irStmt.accept(this, data)
            if (irStmt is IrThrow ||
                irStmt is IrReturn ||
                irStmt is IrBreakContinue ||
                (irStmt is IrCall && irStmt.type.isNothing())
            ) {
                break
            }
        }
        return normalizeBlockStatements(translated)
    }

    private fun unwrapSingleBlock(statements: List<LuaStatement>): List<LuaStatement> {
        val single = statements.singleOrNull() as? LuaBlock ?: return statements
        return single.statements
    }

    private fun containsDirectLocalDeclaration(statements: List<LuaStatement>): Boolean =
        statements.any { stmt ->
            when (stmt) {
                is LuaStatement.LocalVarDeclaration,
                is LuaStatement.LocalFunctionDeclaration,
                -> true

                is LuaCompositeBlock -> containsDirectLocalDeclaration(stmt.statements)

                else -> false
            }
        }

    private fun stripTailBreaks(statements: MutableList<LuaStatement>) {
        while (statements.lastOrNull() is LuaStatement.Break) {
            statements.removeAt(statements.lastIndex)
        }
        val last = statements.lastOrNull() ?: return
        when (last) {
            is LuaCompositeBlock -> {
                val inner = last.statements.toMutableList()
                stripTailBreaks(inner)
                when (inner.size) {
                    0 -> statements.removeAt(statements.lastIndex)
                    1 -> statements[statements.lastIndex] = inner.single()
                    else -> statements[statements.lastIndex] = LuaCompositeBlock(inner)
                }
            }

            is LuaBlock -> {
                val inner = last.statements.toMutableList()
                stripTailBreaks(inner)
                if (inner.isEmpty()) {
                    statements.removeAt(statements.lastIndex)
                } else {
                    statements[statements.lastIndex] = LuaBlock(statements = inner, scope = last.scope)
                }
            }

            is LuaStatement.If -> {
                val newThen =
                    last.thenBlock.statements
                        .toMutableList()
                        .also(::stripTailBreaks)
                val newElseIfs =
                    last.elseIfs.map { elseIf ->
                        val blockStmts =
                            elseIf.block.statements
                                .toMutableList()
                                .also(::stripTailBreaks)
                        LuaStatement.If.ElseIf(
                            condition = elseIf.condition,
                            block = LuaBlock(statements = blockStmts, scope = elseIf.block.scope),
                        )
                    }
                val newElse =
                    last.elseBlock?.let { elseBlock ->
                        val blockStmts = elseBlock.statements.toMutableList().also(::stripTailBreaks)
                        LuaBlock(statements = blockStmts, scope = elseBlock.scope).takeIf { it.statements.isNotEmpty() }
                    }
                statements[statements.lastIndex] =
                    LuaStatement.If(
                        condition = last.condition,
                        thenBlock = LuaBlock(statements = newThen, scope = last.thenBlock.scope),
                        elseIfs = newElseIfs,
                        elseBlock = newElse,
                    )
            }

            else -> {
                Unit
            }
        }
    }

    private fun negateCondition(condition: LuaExpression): LuaExpression =
        when (condition) {
            is LuaExpression.UnaryOp if condition.op == LuaUnaryOperator.Not -> {
                condition.operand
            }

            is LuaExpression.BinaryOp if condition.op == LuaBinaryOperator.Eq -> {
                LuaExpression.BinaryOp(condition.left, LuaBinaryOperator.Neq, condition.right)
            }

            is LuaExpression.BinaryOp if condition.op == LuaBinaryOperator.Neq -> {
                LuaExpression.BinaryOp(condition.left, LuaBinaryOperator.Eq, condition.right)
            }

            else -> {
                LuaExpression.UnaryOp(LuaUnaryOperator.Not, condition)
            }
        }
}
