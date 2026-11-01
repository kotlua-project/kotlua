package io.github.kotlua.ir.lower

import org.jetbrains.kotlin.backend.common.lower.InnerClassesSupport
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrField

class LuaInnerClassesSupport : InnerClassesSupport {
    override fun getOuterThisField(innerClass: IrClass): IrField {
        TODO("Not yet implemented")
    }

    override fun getInnerClassConstructorWithOuterThisParameter(innerClassConstructor: IrConstructor): IrConstructor {
        TODO("Not yet implemented")
    }

    override fun getInnerClassOriginalPrimaryConstructorOrNull(innerClass: IrClass): IrConstructor? {
        TODO("Not yet implemented")
    }
}
