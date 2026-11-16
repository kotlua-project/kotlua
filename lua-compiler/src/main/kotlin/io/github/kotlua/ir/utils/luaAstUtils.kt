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
package io.github.kotlua.ir.utils

import io.github.kotlua.ast.LuaBlock
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.scope.LuaScope
import io.github.kotlua.ir.transformers.IrElementToLuaExpressionTransformer
import io.github.kotlua.ir.transformers.translateLuaCodeIntoStatementList
import io.github.kotlua.name.LuaStandardClassIds
import org.jetbrains.kotlin.backend.common.compilationException
import org.jetbrains.kotlin.ir.backend.js.utils.realOverrideTarget
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrAnnotation
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.irAttribute
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.isElseBranch
import org.jetbrains.kotlin.ir.util.nonDispatchParameters
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

internal fun translateCall(
    expression: IrCall,
    data: LuaGenerationContext,
    exprTransformer: IrElementToLuaExpressionTransformer,
): LuaExpression {
    data.staticContext.intrinsics[expression.symbol]?.let { intrinsic ->
        return intrinsic(expression, data)
    }

    val function = expression.symbol.owner.realOverrideTarget
    val target =
        if (function.parent is IrFunction) {
            data.getNameForValueDeclaration(function)
        } else {
            data.getNameForStaticDeclaration(function)
        }

    val args =
        function.nonDispatchParameters.map { param ->
            expression.arguments[param.indexInParameters]
                ?.accept(exprTransformer, data)
                ?: LuaExpression.NilLiteral
        }

    return LuaExpression.FunctionCall(target = target, args = args)
}

internal inline fun buildLuaIf(
    expression: IrWhen,
    data: LuaGenerationContext,
    parentScope: LuaScope,
    translateBranch: (IrExpression, LuaScope) -> LuaBlock,
): LuaStatement.If {
    val exprTransformer = IrElementToLuaExpressionTransformer()
    val branches = expression.branches

    val elseBranch: IrBranch? = branches.lastOrNull()?.takeIf { isElseBranch(it) }
    val conditionalBranches = if (elseBranch != null) branches.dropLast(1) else branches

    val first = conditionalBranches.first()
    val thenCondition = first.condition.accept(exprTransformer, data)
    val thenBlock = translateBranch(first.result, parentScope)

    val elseIfs =
        conditionalBranches.drop(1).map { branch ->
            LuaStatement.If.ElseIf(
                condition = branch.condition.accept(exprTransformer, data),
                block = translateBranch(branch.result, parentScope),
            )
        }

    val elseBlock = elseBranch?.let { translateBranch(it.result, parentScope) }

    return LuaStatement.If(
        condition = thenCondition,
        thenBlock = thenBlock,
        elseIfs = elseIfs,
        elseBlock = elseBlock,
    )
}

private var IrFunction.cachedOutlinedLuaCode: LuaExpression.AnonymousFunction? by irAttribute(copyByDefault = false)

fun IrFunction.getLuaCode(): LuaExpression.AnonymousFunction? {
    cachedOutlinedLuaCode?.let {
        return it
    }

    parseLuaFromAnnotation(this, LuaStandardClassIds.Annotations.LuaOutlinedFunction, "luaFunctionExpression")
        ?.let { (_, parsedLuaFunction) ->
            cachedOutlinedLuaCode = parsedLuaFunction
            return parsedLuaFunction
        }

    return null
}

private fun parseLuaFromAnnotation(
    declaration: IrDeclaration,
    annotationClassId: ClassId,
    annotationParam: String,
): Pair<IrAnnotation, LuaExpression.AnonymousFunction>? {
    val annotation =
        declaration.getAnnotation(annotationClassId.asSingleFqName())
            ?: return null
    val luaCode =
        annotation.argumentMapping[Name.identifier(annotationParam)]
            ?: compilationException("@${annotationClassId.shortClassName} annotation must contain the Lua code argument", annotation)
    val statements =
        translateLuaCodeIntoStatementList(luaCode)
            ?: compilationException("Could not parse Lua code", annotation)

    val parsedLuaFunction =
        (statements.singleOrNull() as? LuaStatement.Return)
            ?.values
            ?.singleOrNull() as? LuaExpression.AnonymousFunction
            ?: compilationException("Provided Lua code is not a lua function", annotation)

    return annotation to parsedLuaFunction
}
