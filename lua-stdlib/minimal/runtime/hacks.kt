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
internal fun THROW_IAE(msg: String): Nothing {
    lua("error('IllegalArgumentException: ' .. msg)")
    unreachable()
}

@PublishedApi
@UsedFromCompilerGeneratedCode
internal fun THROW_CCE(): Nothing {
    lua("error('ClassCastException')")
    unreachable()
}

@UsedFromCompilerGeneratedCode
internal fun noWhenBranchMatchedException(): Nothing =
    lua("error('NoWhenBranchMatchedException')")

@PublishedApi
@UsedFromCompilerGeneratedCode
internal fun <T : Any> ensureNotNull(v: T?): T = v ?: THROW_NPE()
