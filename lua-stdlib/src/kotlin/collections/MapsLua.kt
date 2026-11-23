package kotlin.collections


/**
 * Returns a new read-only map, mapping only the specified key to the
 * specified value.
 *
 * The returned map is serializable (JVM).
 *
 * @sample samples.collections.Maps.Instantiation.mapFromPairs
 */
@SinceKotlin("1.9")
@Suppress("UNCHECKED_CAST")
public actual fun <K, V> mapOf(pair: Pair<K, V>): Map<K, V> =
    LinkedHashMap<Any?, Any?>(1).apply { put(pair.first, pair.second) }.build() as Map<K, V>

@PublishedApi
@SinceKotlin("1.3")
@kotlin.internal.InlineOnly
internal actual inline fun <K, V> buildMapInternal(builderAction: MutableMap<K, V>.() -> Unit): Map<K, V> =
    LinkedHashMap<K, V>().apply(builderAction).build()

@PublishedApi
@SinceKotlin("1.3")
@kotlin.internal.InlineOnly
internal actual inline fun <K, V> buildMapInternal(capacity: Int, builderAction: MutableMap<K, V>.() -> Unit): Map<K, V> =
    LinkedHashMap<K, V>(capacity).apply(builderAction).build()

/**
 * Calculate the initial capacity of a map.
 */
@PublishedApi
internal actual fun mapCapacity(expectedSize: Int): Int =
    when {
        expectedSize < 0 -> expectedSize
        expectedSize < 3 -> expectedSize + 1
        expectedSize < (1 shl 30) -> (expectedSize / 0.75f + 1.0f).toInt()
        else -> Int.MAX_VALUE
    }
