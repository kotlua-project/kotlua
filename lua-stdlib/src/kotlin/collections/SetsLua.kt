package kotlin.collections

/**
 * Returns a new read-only set containing only the specified object [element].
 *
 * The returned set is serializable (JVM).
 *
 * @sample samples.collections.Collections.Sets.singletonReadOnlySet
 */
@SinceKotlin("1.9")
@Suppress("UNCHECKED_CAST")
public actual fun <T> setOf(element: T): Set<T> =
    LinkedHashSet<Any?>(1).apply { add(element) }.build() as Set<T>

@PublishedApi
@SinceKotlin("1.3")
@kotlin.internal.InlineOnly
internal actual inline fun <E> buildSetInternal(builderAction: MutableSet<E>.() -> Unit): Set<E> =
    LinkedHashSet<E>().apply(builderAction).build()

@PublishedApi
@SinceKotlin("1.3")
@kotlin.internal.InlineOnly
internal actual inline fun <E> buildSetInternal(capacity: Int, builderAction: MutableSet<E>.() -> Unit): Set<E> =
    LinkedHashSet<E>(capacity).apply(builderAction).build()
