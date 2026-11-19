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

import io.github.kotlua.ast.LuaName
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ir.utils.LuaGenerationContext
import io.github.kotlua.ir.utils.getLuaCode
import io.github.kotlua.ir.utils.luaElementAccess
import io.github.kotlua.ir.utils.translateFunctionToAnonymousFunction
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.parentAsClass

class IrFunctionToLuaTransformer : BaseIrElementToLuaNodeTransformer<LuaStatement, LuaGenerationContext>() {
    override fun visitSimpleFunction(
        declaration: IrSimpleFunction,
        data: LuaGenerationContext,
    ): LuaStatement {
        val fnExpr = declaration.getLuaCode() ?: translateFunctionToAnonymousFunction(declaration, data)

        val target =
            when {
                declaration.parent is IrFunction -> {
                    data.getNameForValueDeclaration(declaration)
                }

                declaration.dispatchReceiverParameter != null -> {
                    luaElementAccess(
                        data.getNameForMemberFunction(declaration),
                        data.getRefForStaticDeclaration(declaration.parentAsClass),
                    )
                }

                else -> {
                    data.getRefForStaticDeclaration(declaration)
                }
            }

        return if (declaration.parent is IrFunction) {
            LuaStatement.LocalFunctionDeclaration(
                name = target as LuaName,
                function = fnExpr,
            )
        } else {
            LuaStatement.FunctionDeclaration(
                target = target,
                methodName = null,
                function = fnExpr,
            )
        }
    }

    override fun visitConstructor(
        declaration: IrConstructor,
        data: LuaGenerationContext,
    ): LuaStatement {
        val fnExpr = translateFunctionToAnonymousFunction(declaration, data)
        val classRef = data.getRefForStaticDeclaration(declaration.parentAsClass)
        val ctorName = data.staticContext.irNamer.getNameForConstructor(declaration)
        return LuaStatement.FunctionDeclaration(
            target = luaElementAccess(ctorName, classRef),
            methodName = null,
            function = fnExpr,
        )
    }
}
