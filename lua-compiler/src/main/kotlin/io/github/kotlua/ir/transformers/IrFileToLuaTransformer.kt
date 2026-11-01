package io.github.kotlua.ir.transformers

import io.github.kotlua.ast.LuaBlock
import io.github.kotlua.ast.LuaStatement
import io.github.kotlua.ir.utils.LuaGenerationContext
import io.github.kotlua.ir.utils.LuaStaticContext
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI

@OptIn(UnsafeDuringIrConstructionAPI::class)
class IrFileToLuaTransformer : BaseIrElementToLuaNodeTransformer<LuaBlock, LuaStaticContext>() {
    override fun visitFile(declaration: IrFile, data: LuaStaticContext): LuaBlock {
        val fileContext = LuaGenerationContext(
            staticContext = data,
        )

        return LuaBlock(
            statements = declaration.declarations.map {
                it.accept(IrDeclarationToLuaTransformer(), fileContext)
            },
        )
    }
}
