package io.github.kotlua.ir.transformers

import io.github.kotlua.ast.LuaNode
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.visitors.IrVisitor

abstract class BaseIrElementToLuaNodeTransformer<out R : LuaNode, in D> : IrVisitor<R, D>() {
    override fun visitElement(element: IrElement, data: D): R {
        TODO(element::class.java.simpleName + "is not supported yet")
    }
}
