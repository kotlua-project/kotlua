@file:OptIn(LuaIntrinsic::class)

package kotlin

import kotlin.internal.UsedFromCompilerGeneratedCode

@PublishedApi
@UsedFromCompilerGeneratedCode
internal fun THROW_NPE(): Nothing {
    lua("error('NullPointerException')")
    unreachable()
}


@PublishedApi
@UsedFromCompilerGeneratedCode
internal fun THROW_CCE(): Nothing {
    lua("error('ClassCastException')")
    unreachable()
}

@PublishedApi
@UsedFromCompilerGeneratedCode
internal fun <T : Any> ensureNotNull(v: T?): T = v ?: THROW_NPE()
