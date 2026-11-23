package kotlin.enums

import kotlin.internal.UsedFromCompilerGeneratedCode

@SinceKotlin("1.9")
@PublishedApi
@UsedFromCompilerGeneratedCode
internal actual fun <T : Enum<T>> enumEntriesIntrinsic(): EnumEntries<T> {
    throw NotImplementedError()
}

@PublishedApi
@UsedFromCompilerGeneratedCode
internal fun <E : Enum<E>> createEnumEntries(entries: Array<E>): EnumEntries<E> = enumEntries(entries)