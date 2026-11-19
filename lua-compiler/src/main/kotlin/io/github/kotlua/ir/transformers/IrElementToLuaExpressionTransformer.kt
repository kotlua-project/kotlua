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
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaLValue
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.LuaUnaryOperator
import io.github.kotlua.ast.scope.LuaFunctionScope
import io.github.kotlua.ast.scope.LuaLocalScope
import io.github.kotlua.ast.scope.LuaScope
import io.github.kotlua.ir.utils.LuaGenerationContext
import io.github.kotlua.ir.utils.buildLuaIf
import io.github.kotlua.ir.utils.luaElementAccess
import io.github.kotlua.ir.utils.resolveFieldAccess
import io.github.kotlua.ir.utils.translateCall
import io.github.kotlua.ir.utils.translateFunctionToAnonymousFunction
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDynamicMemberExpression
import org.jetbrains.kotlin.ir.expressions.IrDynamicOperator
import org.jetbrains.kotlin.ir.expressions.IrDynamicOperatorExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrRawFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.left
import org.jetbrains.kotlin.ir.expressions.right
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.ir.util.isElseBranch

class IrElementToLuaExpressionTransformer : BaseIrElementToLuaNodeTransformer<LuaExpression, LuaGenerationContext>() {
    override fun visitMemberAccess(
        expression: IrMemberAccessExpression<*>,
        data: LuaGenerationContext,
    ): LuaExpression = super.visitMemberAccess(expression, data)

    override fun visitBlock(
        expression: IrBlock,
        data: LuaGenerationContext,
    ): LuaExpression = translateContainerExpression(expression, data)

    override fun visitComposite(
        expression: IrComposite,
        data: LuaGenerationContext,
    ): LuaExpression = translateContainerExpression(expression, data)

    private fun translateContainerExpression(
        expression: IrContainerExpression,
        data: LuaGenerationContext,
    ): LuaExpression {
        val stmts = expression.statements
        if (stmts.isEmpty()) return LuaExpression.NilLiteral
        if (stmts.size == 1 && stmts[0] is IrExpression && !stmts[0].let { (it as IrExpression).type.isNothing() }) {
            return stmts[0].accept(this, data)
        }
        val fnScope = LuaFunctionScope(data.currentScope)
        val block = translateBranchResultAsReturnBlock(expression, data, fnScope)
        val anonFn =
            LuaExpression.AnonymousFunction(
                parameters = emptyList(),
                block = block,
                scope = fnScope,
                isVararg = false,
            )
        return LuaExpression.FunctionCall(target = anonFn, args = emptyList())
    }

    override fun visitVararg(
        expression: IrVararg,
        data: LuaGenerationContext,
    ): LuaExpression = super.visitVararg(expression, data)

    override fun visitExpressionBody(
        body: IrExpressionBody,
        data: LuaGenerationContext,
    ): LuaExpression = body.expression.accept(this, data)

    override fun visitFunctionExpression(
        expression: IrFunctionExpression,
        data: LuaGenerationContext,
    ): LuaExpression = translateFunctionToAnonymousFunction(expression.function, data)

    override fun visitConst(
        expression: IrConst,
        data: LuaGenerationContext,
    ): LuaExpression {
        val kind = expression.kind
        return when (kind) {
            IrConstKind.Boolean -> LuaExpression.BooleanLiteral(expression.value as Boolean)
            IrConstKind.Byte -> LuaExpression.IntLiteral((expression.value as Byte).toInt())
            IrConstKind.Char -> LuaExpression.StringLiteral((expression.value as Char).toString())
            IrConstKind.Double -> LuaExpression.DoubleLiteral(expression.value as Double)
            IrConstKind.Float -> LuaExpression.DoubleLiteral((expression.value as Float).toDouble())
            IrConstKind.Int -> LuaExpression.IntLiteral(expression.value as Int)
            IrConstKind.Long -> LuaExpression.LongLiteral(expression.value as Long)
            IrConstKind.Null -> LuaExpression.NilLiteral
            IrConstKind.Short -> LuaExpression.IntLiteral((expression.value as Short).toInt())
            IrConstKind.String -> LuaExpression.StringLiteral(expression.value as String)
        }
    }

    override fun visitStringConcatenation(
        expression: IrStringConcatenation,
        data: LuaGenerationContext,
    ): LuaExpression {
        if (expression.arguments.isEmpty()) {
            return LuaExpression.StringLiteral("")
        }
        return expression.arguments
            .map { it.accept(this, data) }
            .reduce { acc, expr ->
                LuaExpression.BinaryOp(acc, LuaBinaryOperator.Concat, expr)
            }
    }

    override fun visitGetField(
        expression: IrGetField,
        data: LuaGenerationContext,
    ): LuaExpression = resolveFieldAccess(expression, data, this)

    override fun visitGetValue(
        expression: IrGetValue,
        data: LuaGenerationContext,
    ): LuaExpression = data.getNameForValueDeclaration(expression.symbol.owner)

    override fun visitGetObjectValue(
        expression: IrGetObjectValue,
        data: LuaGenerationContext,
    ): LuaExpression {
        if (expression.type.isUnit()) return LuaExpression.NilLiteral
        return super.visitGetObjectValue(expression, data)
    }

    override fun visitSetField(
        expression: IrSetField,
        data: LuaGenerationContext,
    ): LuaExpression {
        val dest = resolveFieldAccess(expression, data, this)
        val source = expression.value.accept(this, data)
        val fnScope = LuaFunctionScope(data.currentScope)
        val assignment = LuaStatement.Assignment(targets = listOf(dest), values = listOf(source))
        val anonFn =
            LuaExpression.AnonymousFunction(
                parameters = emptyList(),
                isVararg = false,
                block = LuaBlock(listOf(assignment), fnScope),
                scope = fnScope,
            )
        return LuaExpression.FunctionCall(target = anonFn, args = emptyList())
    }

    override fun visitSetValue(
        expression: IrSetValue,
        data: LuaGenerationContext,
    ): LuaExpression = super.visitSetValue(expression, data)

    override fun visitDelegatingConstructorCall(
        expression: IrDelegatingConstructorCall,
        data: LuaGenerationContext,
    ): LuaExpression = super.visitDelegatingConstructorCall(expression, data)

    override fun visitConstructorCall(
        expression: IrConstructorCall,
        data: LuaGenerationContext,
    ): LuaExpression {
        data.staticContext.expressionIntrinsics[expression.symbol]?.let { intrinsic ->
            return intrinsic(expression, data)
        }
        return super.visitConstructorCall(expression, data)
    }

    override fun visitCall(
        expression: IrCall,
        data: LuaGenerationContext,
    ): LuaExpression {
        if (data.checkIfLuaCode(expression.symbol) || data.checkIfHasAssociatedLuaCode(expression.symbol)) {
            return LuaCallTransformer(expression, data).generateExpression()
        }
        return translateCall(expression, data, this)
    }

    override fun visitWhen(
        expression: IrWhen,
        data: LuaGenerationContext,
    ): LuaExpression {
        if (expression.origin == IrStatementOrigin.ANDAND) {
            return LuaExpression.BinaryOp(
                expression.branches[0].condition.accept(this, data),
                LuaBinaryOperator.And,
                expression.branches[0].result.accept(this, data),
            )
        }
        if (expression.origin == IrStatementOrigin.OROR) {
            return LuaExpression.BinaryOp(
                expression.branches[0].condition.accept(this, data),
                LuaBinaryOperator.Or,
                expression.branches[1].result.accept(this, data),
            )
        }

        val singleBranch = expression.branches.singleOrNull()
        if (singleBranch != null && isElseBranch(singleBranch)) {
            return singleBranch.result.accept(this, data)
        }

        val fnScope = LuaFunctionScope(data.currentScope)
        val ifStatement =
            buildLuaIf(expression, data, fnScope) { branchResult, blockScope ->
                translateBranchResultAsReturnBlock(branchResult, data, blockScope)
            }

        val anonFn =
            LuaExpression.AnonymousFunction(
                parameters = emptyList(),
                block = LuaBlock(listOf(ifStatement), fnScope),
                scope = fnScope,
                isVararg = false,
            )
        return LuaExpression.FunctionCall(target = anonFn, args = emptyList())
    }

    private fun translateBranchResultAsReturnBlock(
        result: IrExpression,
        data: LuaGenerationContext,
        parentScope: LuaScope,
    ): LuaBlock {
        val blockScope = LuaLocalScope(parentScope)
        val stmtTransformer = IrElementToLuaStatementTransformer()

        if (result is IrContainerExpression) {
            val stmts = result.statements
            if (stmts.isEmpty()) {
                return LuaBlock(emptyList(), blockScope)
            }
            val luaStmts = stmts.dropLast(1).map { it.accept(stmtTransformer, data) }.toMutableList()
            val last = stmts.last()
            luaStmts +=
                if (last is IrExpression && !last.type.isNothing() && !result.type.isUnit()) {
                    LuaStatement.Return(listOf(last.accept(this, data)))
                } else {
                    last.accept(stmtTransformer, data)
                }
            return LuaBlock(luaStmts, blockScope)
        }

        val luaStatements =
            if (result.type.isNothing()) {
                listOf(result.accept(stmtTransformer, data))
            } else if (result.type.isUnit()) {
                listOf(result.accept(stmtTransformer, data))
            } else {
                listOf(LuaStatement.Return(listOf(result.accept(this, data))))
            }
        return LuaBlock(luaStatements, blockScope)
    }

    override fun visitTypeOperator(
        expression: IrTypeOperatorCall,
        data: LuaGenerationContext,
    ): LuaExpression =
        when (expression.operator) {
            IrTypeOperator.IMPLICIT_CAST,
            IrTypeOperator.IMPLICIT_DYNAMIC_CAST,
            IrTypeOperator.IMPLICIT_COERCION_TO_UNIT,
            IrTypeOperator.IMPLICIT_INTEGER_COERCION,
            IrTypeOperator.IMPLICIT_NOTNULL,
            IrTypeOperator.REINTERPRET_CAST,
            -> {
                expression.argument.accept(this, data)
            }

            IrTypeOperator.CAST -> {
                expression.argument.accept(this, data)
            }

            IrTypeOperator.SAFE_CAST,
            IrTypeOperator.INSTANCEOF,
            IrTypeOperator.NOT_INSTANCEOF,
            IrTypeOperator.SAM_CONVERSION,
            -> {
                TODO("Type operator ${expression.operator} is not implemented yet")
            }
        }

    override fun visitDynamicMemberExpression(
        expression: IrDynamicMemberExpression,
        data: LuaGenerationContext,
    ): LuaExpression = luaElementAccess(expression.memberName, expression.receiver.accept(this, data))

    override fun visitDynamicOperatorExpression(
        expression: IrDynamicOperatorExpression,
        data: LuaGenerationContext,
    ): LuaExpression =
        when (expression.operator) {
            IrDynamicOperator.UNARY_PLUS -> {
                expression.receiver.accept(this, data)
            }

            IrDynamicOperator.UNARY_MINUS -> {
                unaryOperation(LuaUnaryOperator.Negate, expression, data)
            }

            IrDynamicOperator.EXCL -> {
                unaryOperation(LuaUnaryOperator.Not, expression, data)
            }

            IrDynamicOperator.BINARY_PLUS -> {
                binaryOperation(LuaBinaryOperator.Add, expression, data)
            }

            IrDynamicOperator.BINARY_MINUS -> {
                binaryOperation(LuaBinaryOperator.Subtract, expression, data)
            }

            IrDynamicOperator.MUL -> {
                binaryOperation(LuaBinaryOperator.Multiply, expression, data)
            }

            IrDynamicOperator.DIV -> {
                binaryOperation(LuaBinaryOperator.Divide, expression, data)
            }

            IrDynamicOperator.MOD -> {
                binaryOperation(LuaBinaryOperator.Modulo, expression, data)
            }

            IrDynamicOperator.GT -> {
                binaryOperation(LuaBinaryOperator.Gt, expression, data)
            }

            IrDynamicOperator.LT -> {
                binaryOperation(LuaBinaryOperator.Lt, expression, data)
            }

            IrDynamicOperator.GE -> {
                binaryOperation(LuaBinaryOperator.Gte, expression, data)
            }

            IrDynamicOperator.LE -> {
                binaryOperation(LuaBinaryOperator.Lte, expression, data)
            }

            IrDynamicOperator.EQEQ,
            IrDynamicOperator.EQEQEQ,
            -> {
                binaryOperation(LuaBinaryOperator.Eq, expression, data)
            }

            IrDynamicOperator.EXCLEQ,
            IrDynamicOperator.EXCLEQEQ,
            -> {
                binaryOperation(LuaBinaryOperator.Neq, expression, data)
            }

            IrDynamicOperator.ANDAND -> {
                binaryOperation(LuaBinaryOperator.And, expression, data)
            }

            IrDynamicOperator.OROR -> {
                binaryOperation(LuaBinaryOperator.Or, expression, data)
            }

            IrDynamicOperator.ARRAY_ACCESS -> {
                LuaLValue.TableIndex(
                    target = expression.left.accept(this, data),
                    index = expression.right.accept(this, data),
                )
            }

            IrDynamicOperator.INVOKE -> {
                LuaExpression.FunctionCall(
                    target = expression.receiver.accept(this, data),
                    args = expression.arguments.map { it.accept(this, data) },
                )
            }

            IrDynamicOperator.EQ -> {
                val dest = expression.left.accept(this, data) as LuaLValue
                val source = expression.right.accept(this, data)
                val fnScope = LuaFunctionScope(data.currentScope)
                val assignment = LuaStatement.Assignment(targets = listOf(dest), values = listOf(source))
                val anonFn =
                    LuaExpression.AnonymousFunction(
                        parameters = emptyList(),
                        isVararg = false,
                        block = LuaBlock(listOf(assignment), fnScope),
                        scope = fnScope,
                    )
                LuaExpression.FunctionCall(target = anonFn, args = emptyList())
            }

            IrDynamicOperator.PREFIX_INCREMENT,
            IrDynamicOperator.PREFIX_DECREMENT,
            IrDynamicOperator.POSTFIX_INCREMENT,
            IrDynamicOperator.POSTFIX_DECREMENT,
            IrDynamicOperator.PLUSEQ,
            IrDynamicOperator.MINUSEQ,
            IrDynamicOperator.MULEQ,
            IrDynamicOperator.DIVEQ,
            IrDynamicOperator.MODEQ,
            -> {
                TODO("Dynamic operator ${expression.operator} is not implemented yet")
            }
        }

    override fun visitRawFunctionReference(
        expression: IrRawFunctionReference,
        data: LuaGenerationContext,
    ): LuaExpression = super.visitRawFunctionReference(expression, data)

    private fun unaryOperation(
        operator: LuaUnaryOperator,
        expression: IrDynamicOperatorExpression,
        data: LuaGenerationContext,
    ) = LuaExpression.UnaryOp(operator, expression.receiver.accept(this, data))

    private fun binaryOperation(
        operator: LuaBinaryOperator,
        expression: IrDynamicOperatorExpression,
        data: LuaGenerationContext,
    ) = LuaExpression.BinaryOp(
        expression.left.accept(this, data),
        operator,
        expression.right.accept(this, data),
    )
}
