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
import io.github.kotlua.ir.lower.LuaCodeOutliningLowering
import io.github.kotlua.ir.utils.LuaGenerationContext
import io.github.kotlua.name.LuaStandardClassIds
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrScript
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal

class IrDeclarationToLuaTransformer : BaseIrElementToLuaNodeTransformer<LuaStatement, LuaGenerationContext>() {
    override fun visitSimpleFunction(
        declaration: IrSimpleFunction,
        data: LuaGenerationContext,
    ): LuaStatement {
        require(!declaration.isExpect)
        if (declaration.isExternal ||
            declaration.body == null ||
            declaration.origin == LuaCodeOutliningLowering.OUTLINED_LUA_CODE_ORIGIN ||
            declaration.hasAnnotation(LuaStandardClassIds.Annotations.LuaIntrinsic)
        ) {
            return LuaStatement.Empty
        }
        return declaration.accept(IrFunctionToLuaTransformer(), data)
    }

    override fun visitProperty(
        declaration: IrProperty,
        data: LuaGenerationContext,
    ): LuaStatement {
        if (declaration.isEffectivelyExternal()) return LuaStatement.Empty
        return super.visitProperty(declaration, data)
    }

    override fun visitConstructor(
        declaration: IrConstructor,
        data: LuaGenerationContext,
    ): LuaStatement {
        if (declaration.isExternal) return LuaStatement.Empty
        return declaration.accept(IrFunctionToLuaTransformer(), data)
    }

    override fun visitClass(
        declaration: IrClass,
        data: LuaGenerationContext,
    ): LuaStatement = LuaStatement.Empty

    override fun visitField(
        declaration: IrField,
        data: LuaGenerationContext,
    ): LuaStatement {
        if (declaration.isExternal) return LuaStatement.Empty

        val fieldRef = data.getRefForStaticDeclaration(declaration)
        declaration.initializer?.let { irInitializer ->
            val initializer = irInitializer.accept(IrElementToLuaExpressionTransformer(), data)
            data.staticContext.initializerBlock +=
                LuaStatement.Assignment(
                    targets = listOf(fieldRef),
                    values = listOf(initializer),
                )
        }
        return LuaStatement.Empty
    }

    override fun visitVariable(
        declaration: IrVariable,
        data: LuaGenerationContext,
    ): LuaStatement = super.visitVariable(declaration, data)

    override fun visitScript(
        declaration: IrScript,
        data: LuaGenerationContext,
    ): LuaStatement = super.visitScript(declaration, data)
}
