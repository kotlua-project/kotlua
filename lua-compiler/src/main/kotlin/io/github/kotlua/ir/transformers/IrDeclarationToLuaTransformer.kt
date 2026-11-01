package io.github.kotlua.ir.transformers

import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ir.utils.LuaGenerationContext
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrScript
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable

class IrDeclarationToLuaTransformer : BaseIrElementToLuaNodeTransformer<LuaStatement, LuaGenerationContext>() {
    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: LuaGenerationContext): LuaStatement {
        require(!declaration.isExpect)
        return declaration.accept(IrFunctionToLuaTransformer(), data)
    }

    override fun visitConstructor(declaration: IrConstructor, data: LuaGenerationContext): LuaStatement {
        return super.visitConstructor(declaration, data)
    }

    override fun visitClass(declaration: IrClass, data: LuaGenerationContext): LuaStatement {
        return super.visitClass(declaration, data)
    }

    override fun visitField(declaration: IrField, data: LuaGenerationContext): LuaStatement {
        return super.visitField(declaration, data)
    }

    override fun visitVariable(declaration: IrVariable, data: LuaGenerationContext): LuaStatement {
        return super.visitVariable(declaration, data)
    }

    override fun visitScript(declaration: IrScript, data: LuaGenerationContext): LuaStatement {
        return super.visitScript(declaration, data)
    }
}