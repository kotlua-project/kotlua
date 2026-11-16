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

import io.github.kotlua.ast.LuaBlock
import io.github.kotlua.ast.LuaCompositeBlock
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaName
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.scope.LuaLocalScope
import io.github.kotlua.ir.utils.LuaGenerationContext
import io.github.kotlua.ir.utils.buildLuaIf
import io.github.kotlua.ir.utils.translateCall
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.irError
import org.jetbrains.kotlin.ir.util.isElseBranch

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
            statements = body.statements.map { it.accept(this, data) },
            scope = data.currentScope,
        )

    override fun visitBlock(
        expression: IrBlock,
        data: LuaGenerationContext,
    ): LuaStatement {
        val blockScope = LuaLocalScope(data.currentScope)
        val blockContext = LuaGenerationContext(data.staticContext, blockScope, data.localNames)
        return LuaBlock(
            statements = expression.statements.map { it.accept(this, blockContext) },
            scope = blockScope,
        )
    }

    override fun visitComposite(
        expression: IrComposite,
        data: LuaGenerationContext,
    ): LuaStatement {
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

            is LuaExpression.NilLiteral -> {
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
        if (expression.value is IrGetObjectValue && expression.value.type.isUnit()) {
            return LuaStatement.Return(emptyList())
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
        irError("Unsupported operation") { withIrEntry("operation", jump) }
    }

    override fun visitCall(
        expression: IrCall,
        data: LuaGenerationContext,
    ): LuaStatement {
        if (data.checkIfLuaCode(expression.symbol) || data.checkIfHasAssociatedLuaCode(expression.symbol)) {
            return LuaCallTransformer(expression, data).generateStatement()
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

        return buildLuaIf(expression, data, data.currentScope) { branchResult, parentScope ->
            val blockScope = LuaLocalScope(parentScope)
            val stmts =
                when (branchResult) {
                    is IrContainerExpression -> branchResult.statements.map { it.accept(this, data) }
                    else -> listOf(branchResult.accept(this, data))
                }
            LuaBlock(stmts, blockScope)
        }
    }

    override fun visitTypeOperator(
        expression: IrTypeOperatorCall,
        data: LuaGenerationContext,
    ): LuaStatement =
        when (expression.operator) {
            IrTypeOperator.IMPLICIT_COERCION_TO_UNIT -> expression.argument.accept(this, data)
            else -> super.visitTypeOperator(expression, data)
        }
}
