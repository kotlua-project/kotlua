package io.github.kotlua.ir.utils

import io.github.kotlua.ir.LuaIrBackendContext
import org.jetbrains.kotlin.backend.common.InlineClassesUtils
import org.jetbrains.kotlin.ir.declarations.IrClass

class LuaInlineClassesUtils(val context: LuaIrBackendContext) : InlineClassesUtils {
    override fun isClassInlineLike(klass: IrClass): Boolean {
        return false
    }
}
