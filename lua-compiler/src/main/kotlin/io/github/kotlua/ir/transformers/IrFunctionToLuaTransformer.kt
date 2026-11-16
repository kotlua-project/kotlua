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
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ast.scope.LuaFunctionScope
import io.github.kotlua.ir.utils.LuaGenerationContext
import io.github.kotlua.ir.utils.getLuaCode
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.nonDispatchParameters

class IrFunctionToLuaTransformer : BaseIrElementToLuaNodeTransformer<LuaStatement, LuaGenerationContext>() {
    override fun visitSimpleFunction(
        declaration: IrSimpleFunction,
        data: LuaGenerationContext,
    ): LuaStatement {
        val fnScope = LuaFunctionScope(data.currentScope, declaration.name.asString())

        val fnExpr =
            declaration.getLuaCode() ?: run {
                val fnContext = data.newLocalContext(declaration, fnScope)

                val params =
                    declaration.nonDispatchParameters.map { param ->
                        fnContext.getNameForValueDeclaration(param)
                    }
                val bodyBlock =
                    (declaration.body?.accept(IrElementToLuaStatementTransformer(), fnContext) as? LuaBlock)
                        ?: LuaBlock(emptyList(), fnScope)

                LuaExpression.AnonymousFunction(
                    parameters = params,
                    isVararg = false,
                    block = bodyBlock,
                    scope = fnScope,
                )
            }

        return if (declaration.parent is IrFunction) {
            LuaStatement.LocalFunctionDeclaration(
                name = data.getNameForValueDeclaration(declaration),
                function = fnExpr,
            )
        } else {
            LuaStatement.FunctionDeclaration(
                target = data.staticContext.getNameForStaticDeclaration(declaration),
                methodName = null,
                function = fnExpr,
            )
        }
    }
}
