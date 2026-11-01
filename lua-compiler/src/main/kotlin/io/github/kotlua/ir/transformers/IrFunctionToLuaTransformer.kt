package io.github.kotlua.ir.transformers

import io.github.kotlua.ast.LuaBlock
import io.github.kotlua.ast.LuaExpression
import io.github.kotlua.ast.LuaStatement.LocalFunctionDeclaration
import io.github.kotlua.ir.utils.LuaGenerationContext
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.nonDispatchParameters

class IrFunctionToLuaTransformer : BaseIrElementToLuaNodeTransformer<LocalFunctionDeclaration, LuaGenerationContext>() {
    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: LuaGenerationContext): LocalFunctionDeclaration {
        val block = declaration.body?.accept(IrElementToLuaStatementTransformer(), data) as? LuaBlock ?: LuaBlock(emptyList())
        return LocalFunctionDeclaration(
            name = declaration.name.asString(),
            function = LuaExpression.AnonymousFunction(
                parameters = declaration.nonDispatchParameters.map { param ->
                    param.name.asString()
                },
                isVararg = false,
                block = block
            )
        )
    }

    override fun visitConstructor(declaration: IrConstructor, data: LuaGenerationContext): LocalFunctionDeclaration {
        return super.visitConstructor(declaration, data)
    }
}
