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
package io.github.kotlua.ir.lower

import io.github.kotlua.ir.LuaIrBackendContext
import io.github.kotlua.name.LuaStandardClassIds
import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrTransformer
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid

class LuaTempVariableCleanupLowering(
    val irBackendContext: LuaIrBackendContext,
) : BodyLoweringPass {
    override fun lower(
        irBody: IrBody,
        container: IrDeclaration,
    ) {
        while (runCleanupIteration(irBody)) {
            // Iterate until fixpoint
        }
    }

    private fun runCleanupIteration(irBody: IrBody): Boolean {
        val getCounts = hashMapOf<IrValueSymbol, Int>()
        val setCounts = hashMapOf<IrValueSymbol, Int>()
        val protectedVars = hashSetOf<IrValueSymbol>()

        irBody.acceptVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitComposite(expression: IrComposite) {
                    if (expression.origin == LuaMultiReturnLowering.LUA_MULTI_ASSIGN_ORIGIN) {
                        for (stmt in expression.statements) {
                            if (stmt is IrVariable) {
                                protectedVars += stmt.symbol
                            }
                        }
                    }
                    super.visitComposite(expression)
                }

                override fun visitGetValue(expression: IrGetValue) {
                    getCounts[expression.symbol] = (getCounts[expression.symbol] ?: 0) + 1
                    super.visitGetValue(expression)
                }

                override fun visitSetValue(expression: IrSetValue) {
                    setCounts[expression.symbol] = (setCounts[expression.symbol] ?: 0) + 1
                    super.visitSetValue(expression)
                }
            },
        )

        fun isImmutableSymbol(symbol: IrValueSymbol): Boolean =
            when (val owner = symbol.owner) {
                is IrValueParameter -> (setCounts[symbol] ?: 0) == 0
                is IrVariable -> !owner.isVar && (setCounts[symbol] ?: 0) == 0 && symbol !in protectedVars
                else -> false
            }

        var changed = false
        val aliasMap = hashMapOf<IrValueSymbol, IrExpression>()

        fun resolveReplacement(
            replacement: IrExpression,
            expectedType: IrType,
        ): IrExpression {
            val copy = replacement.deepCopyWithSymbols()
            if (copy.type == expectedType) return copy
            return IrTypeOperatorCallImpl(
                startOffset = copy.startOffset,
                endOffset = copy.endOffset,
                type = expectedType,
                operator = IrTypeOperator.REINTERPRET_CAST,
                typeOperand = expectedType,
                argument = copy,
            )
        }

        fun processStatements(
            statements: MutableList<IrStatement>,
            inStatementPosition: Boolean,
            transformer: IrTransformer<Boolean>,
        ) {
            val transformed = ArrayList<IrStatement>(statements.size)
            val lastIndex = statements.lastIndex
            for ((idx, stmt) in statements.withIndex()) {
                val stmtIsStatementPos = inStatementPosition || idx < lastIndex
                val newStmt = stmt.transform(transformer, stmtIsStatementPos) as IrStatement
                if (newStmt is IrVariable && newStmt.symbol !in protectedVars) {
                    val sym = newStmt.symbol
                    val gets = getCounts[sym] ?: 0
                    val sets = setCounts[sym] ?: 0
                    val init = newStmt.initializer

                    if (sets == 0 && init != null && isPropagatableInitializer(init, ::isImmutableSymbol)) {
                        aliasMap[sym] = init
                        changed = true
                        continue
                    }

                    if (gets == 0 && sets == 0 && (init == null || isPureExpression(init))) {
                        changed = true
                        continue
                    }
                }
                transformed += newStmt
            }

            if (inStatementPosition) {
                while (transformed.isNotEmpty()) {
                    val last = transformed.last()
                    if (last is IrExpression && isPureExpression(last)) {
                        transformed.removeAt(transformed.lastIndex)
                        changed = true
                    } else {
                        break
                    }
                }
            }

            if (!changed && transformed.size >= 2) {
                var i = transformed.lastIndex - 1
                while (i >= 0) {
                    val stmt = transformed[i]
                    if (stmt is IrVariable && stmt.symbol !in protectedVars) {
                        val sym = stmt.symbol
                        val init = stmt.initializer
                        if (init != null &&
                            (getCounts[sym] ?: 0) == 1 &&
                            (setCounts[sym] ?: 0) == 0 &&
                            isSimpleInlineableExpression(init)
                        ) {
                            val inlinedNext =
                                tryInlineIntoNextStatement(
                                    targetSymbol = sym,
                                    replacement = init,
                                    nextStmt = transformed[i + 1],
                                    isImmutable = ::isImmutableSymbol,
                                )
                            if (inlinedNext != null) {
                                transformed[i + 1] = inlinedNext
                                transformed.removeAt(i)
                                changed = true
                            }
                        }
                    }
                    i--
                }
            }

            statements.clear()
            statements.addAll(transformed)
        }

        val transformer =
            object : IrTransformer<Boolean>() {
                override fun visitDeclaration(
                    declaration: IrDeclarationBase,
                    data: Boolean,
                ): IrStatement = super.visitDeclaration(declaration, false)

                override fun visitExpressionBody(
                    body: IrExpressionBody,
                    data: Boolean,
                ): IrBody = super.visitExpressionBody(body, false)

                override fun visitBlockBody(
                    body: IrBlockBody,
                    data: Boolean,
                ): IrBody {
                    processStatements(body.statements, inStatementPosition = true, this)
                    return body
                }

                override fun visitContainerExpression(
                    expression: IrContainerExpression,
                    data: Boolean,
                ): IrExpression {
                    if (expression.origin == LuaMultiReturnLowering.LUA_MULTI_ASSIGN_ORIGIN) {
                        return super.visitContainerExpression(expression, false)
                    }
                    processStatements(
                        expression.statements,
                        inStatementPosition = data || expression is IrReturnableBlock,
                        this,
                    )
                    return expression
                }

                override fun visitWhen(
                    expression: IrWhen,
                    data: Boolean,
                ): IrExpression {
                    val newBranches =
                        expression.branches.map { branch ->
                            val newCond = branch.condition.transform(this, false)
                            val newRes = branch.result.transform(this, data)
                            if (branch is IrElseBranch) {
                                IrElseBranchImpl(branch.startOffset, branch.endOffset, newCond, newRes)
                            } else {
                                IrBranchImpl(branch.startOffset, branch.endOffset, newCond, newRes)
                            }
                        }
                    return IrWhenImpl(expression.startOffset, expression.endOffset, expression.type, expression.origin, newBranches)
                }

                override fun visitLoop(
                    loop: IrLoop,
                    data: Boolean,
                ): IrExpression {
                    loop.condition = loop.condition.transform(this, false)
                    loop.body = loop.body?.transform(this, true)
                    return loop
                }

                override fun visitTry(
                    aTry: IrTry,
                    data: Boolean,
                ): IrExpression {
                    aTry.tryResult = aTry.tryResult.transform(this, data)
                    val newCatches =
                        aTry.catches.map { catch ->
                            IrCatchImpl(
                                catch.startOffset,
                                catch.endOffset,
                                catch.catchParameter,
                                catch.result.transform(this, data),
                                catch.origin,
                            )
                        }
                    val newFinally = aTry.finallyExpression?.transform(this, true)
                    return IrTryImpl(aTry.startOffset, aTry.endOffset, aTry.type, aTry.tryResult, newCatches, newFinally)
                }

                override fun visitTypeOperator(
                    expression: IrTypeOperatorCall,
                    data: Boolean,
                ): IrExpression {
                    if (expression.operator == IrTypeOperator.IMPLICIT_COERCION_TO_UNIT) {
                        expression.argument = expression.argument.transform(this, true)
                        return expression
                    }
                    return super.visitTypeOperator(expression, false)
                }

                override fun visitExpression(
                    expression: IrExpression,
                    data: Boolean,
                ): IrExpression = super.visitExpression(expression, false)

                override fun visitGetValue(
                    expression: IrGetValue,
                    data: Boolean,
                ): IrExpression {
                    val alias = aliasMap[expression.symbol] ?: return super.visitGetValue(expression, data)
                    changed = true
                    return resolveReplacement(alias, expression.type)
                }
            }

        irBody.transform(transformer, irBody is IrBlockBody)
        return changed
    }

    private tailrec fun unwrapNoOpCasts(expr: IrExpression): IrExpression =
        if (expr is IrTypeOperatorCall &&
            (
                expr.operator == IrTypeOperator.IMPLICIT_CAST ||
                    expr.operator == IrTypeOperator.IMPLICIT_DYNAMIC_CAST ||
                    expr.operator == IrTypeOperator.REINTERPRET_CAST
            )
        ) {
            unwrapNoOpCasts(expr.argument)
        } else {
            expr
        }

    private fun isPropagatableInitializer(
        expr: IrExpression,
        isImmutable: (IrValueSymbol) -> Boolean,
    ): Boolean {
        val unwrapped = unwrapNoOpCasts(expr)
        return when (unwrapped) {
            is IrConst -> unwrapped.kind != IrConstKind.Long
            is IrGetObjectValue -> unwrapped.type.isUnit()
            is IrGetValue -> isImmutable(unwrapped.symbol)
            else -> false
        }
    }

    private fun isPureExpression(expr: IrExpression): Boolean {
        val unwrapped = unwrapNoOpCasts(expr)
        return when (unwrapped) {
            is IrConst -> unwrapped.kind != IrConstKind.Long
            is IrGetObjectValue -> unwrapped.type.isUnit()
            is IrGetValue -> true
            else -> false
        }
    }

    private fun isPureAndImmutableLeaf(
        expr: IrExpression,
        isImmutable: (IrValueSymbol) -> Boolean,
    ): Boolean {
        val unwrapped = unwrapNoOpCasts(expr)
        return when (unwrapped) {
            is IrConst -> unwrapped.kind != IrConstKind.Long
            is IrGetObjectValue -> unwrapped.type.isUnit()
            is IrGetValue -> isImmutable(unwrapped.symbol)
            else -> false
        }
    }

    private fun isSimpleInlineableExpression(expr: IrExpression): Boolean {
        var ok = true
        expr.acceptVoid(
            object : IrVisitorVoid() {
                override fun visitElement(element: IrElement) {
                    if (!ok) return
                    when (element) {
                        is IrContainerExpression,
                        is IrWhen,
                        is IrTry,
                        is IrLoop,
                        is IrFunction,
                        is IrFunctionExpression,
                        is IrSetValue,
                        is IrSetField,
                        is IrThrow,
                        is IrReturn,
                        is IrBreakContinue,
                        -> ok = false

                        is IrDynamicOperatorExpression if element.operator.isAssignmentOperator -> ok = false

                        else -> element.acceptChildrenVoid(this)
                    }
                }
            },
        )
        return ok
    }

    private fun tryInlineIntoNextStatement(
        targetSymbol: IrValueSymbol,
        replacement: IrExpression,
        nextStmt: IrStatement,
        isImmutable: (IrValueSymbol) -> Boolean,
    ): IrStatement? {
        var replaced = false
        var aborted = false

        fun visitExpr(expr: IrExpression): IrExpression {
            if (replaced || aborted) return expr
            if (expr is IrGetValue && expr.symbol == targetSymbol) {
                replaced = true
                return replacement
            }
            if (isPureAndImmutableLeaf(expr, isImmutable)) {
                return expr
            }

            return when (expr) {
                is IrTypeOperatorCall -> {
                    val newArg = visitExpr(expr.argument)
                    if (replaced) expr.apply { argument = newArg } else expr
                }

                is IrGetField -> {
                    val receiver = expr.receiver
                    if (receiver == null) {
                        aborted = true
                        expr
                    } else {
                        val newReceiver = visitExpr(receiver)
                        if (replaced) {
                            IrGetFieldImpl(
                                expr.startOffset,
                                expr.endOffset,
                                expr.symbol,
                                expr.type,
                                newReceiver,
                                expr.origin,
                                expr.superQualifierSymbol,
                            )
                        } else {
                            aborted = true
                            expr
                        }
                    }
                }

                is IrMemberAccessExpression<*> -> {
                    val maxIdx =
                        if (expr is IrCall && expr.symbol.owner.hasAnnotation(LuaStandardClassIds.Annotations.LuaOutlinedFunction)) {
                            0
                        } else {
                            expr.arguments.lastIndex
                        }
                    for (idx in 0..maxIdx) {
                        val arg = expr.arguments[idx] ?: continue
                        val newArg = visitExpr(arg)
                        if (replaced) {
                            expr.arguments[idx] = newArg
                            return expr
                        }
                        if (aborted) return expr
                    }
                    aborted = true
                    expr
                }

                is IrStringConcatenation -> {
                    val args = expr.arguments.toMutableList()
                    for (idx in args.indices) {
                        val newArg = visitExpr(args[idx])
                        if (replaced) {
                            args[idx] = newArg
                            return IrStringConcatenationImpl(expr.startOffset, expr.endOffset, expr.type, args)
                        }
                        if (aborted) return expr
                    }
                    aborted = true
                    expr
                }

                else -> {
                    aborted = true
                    expr
                }
            }
        }

        val updated =
            when (nextStmt) {
                is IrVariable -> {
                    val init = nextStmt.initializer
                    if (init != null) {
                        val newInit = visitExpr(init)
                        if (replaced) {
                            nextStmt.apply { initializer = newInit }
                        } else {
                            nextStmt
                        }
                    } else {
                        nextStmt
                    }
                }

                is IrReturn -> {
                    val newVal = visitExpr(nextStmt.value)
                    if (replaced) {
                        IrReturnImpl(nextStmt.startOffset, nextStmt.endOffset, nextStmt.type, nextStmt.returnTargetSymbol, newVal)
                    } else {
                        nextStmt
                    }
                }

                is IrThrow -> {
                    val newVal = visitExpr(nextStmt.value)
                    if (replaced) {
                        IrThrowImpl(nextStmt.startOffset, nextStmt.endOffset, nextStmt.type, newVal)
                    } else {
                        nextStmt
                    }
                }

                is IrSetValue -> {
                    val newVal = visitExpr(nextStmt.value)
                    if (replaced) {
                        IrSetValueImpl(nextStmt.startOffset, nextStmt.endOffset, nextStmt.type, nextStmt.symbol, newVal, nextStmt.origin)
                    } else {
                        nextStmt
                    }
                }

                is IrSetField -> {
                    val rec = nextStmt.receiver
                    val newRec =
                        if (rec != null) {
                            visitExpr(rec)
                        } else {
                            null
                        }
                    if (replaced) {
                        IrSetFieldImpl(
                            nextStmt.startOffset,
                            nextStmt.endOffset,
                            nextStmt.symbol,
                            newRec,
                            nextStmt.value,
                            nextStmt.type,
                            nextStmt.origin,
                            nextStmt.superQualifierSymbol,
                        )
                    } else if (!aborted && (rec == null || isPureAndImmutableLeaf(rec, isImmutable))) {
                        val newVal = visitExpr(nextStmt.value)
                        if (replaced) {
                            IrSetFieldImpl(
                                nextStmt.startOffset,
                                nextStmt.endOffset,
                                nextStmt.symbol,
                                rec,
                                newVal,
                                nextStmt.type,
                                nextStmt.origin,
                                nextStmt.superQualifierSymbol,
                            )
                        } else {
                            nextStmt
                        }
                    } else {
                        nextStmt
                    }
                }

                is IrExpression -> {
                    visitExpr(nextStmt)
                }

                else -> {
                    nextStmt
                }
            }

        return if (replaced && !aborted) updated else null
    }
}
