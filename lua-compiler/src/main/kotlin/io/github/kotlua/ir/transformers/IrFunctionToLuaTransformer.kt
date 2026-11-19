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

import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ir.utils.LuaGenerationContext
import io.github.kotlua.ir.utils.getLuaCode
import io.github.kotlua.ir.utils.translateFunctionToAnonymousFunction
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction

class IrFunctionToLuaTransformer : BaseIrElementToLuaNodeTransformer<LuaStatement, LuaGenerationContext>() {
    override fun visitSimpleFunction(
        declaration: IrSimpleFunction,
        data: LuaGenerationContext,
    ): LuaStatement {
        val fnExpr = declaration.getLuaCode() ?: translateFunctionToAnonymousFunction(declaration, data)

        return if (declaration.parent is IrFunction) {
            LuaStatement.LocalFunctionDeclaration(
                name = data.getNameForValueDeclaration(declaration),
                function = fnExpr,
            )
        } else {
            LuaStatement.FunctionDeclaration(
                target = data.getRefForStaticDeclaration(declaration),
                methodName = null,
                function = fnExpr,
            )
        }
    }
}
