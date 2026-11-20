package kotlin

import kotlin.internal.UsedFromCompilerGeneratedCode

@PublishedApi
@UsedFromCompilerGeneratedCode
internal fun THROW_NPE(): Nothing = throw NullPointerException()

@PublishedApi
@UsedFromCompilerGeneratedCode
internal fun THROW_IAE(msg: String): Nothing = throw IllegalArgumentException(msg)

@PublishedApi
@UsedFromCompilerGeneratedCode
internal fun THROW_CCE(): Nothing = throw ClassCastException()

@UsedFromCompilerGeneratedCode
internal fun noWhenBranchMatchedException(): Nothing = throw NoWhenBranchMatchedException()

@PublishedApi
@UsedFromCompilerGeneratedCode
internal fun <T : Any> ensureNotNull(v: T?): T = v ?: THROW_NPE()
