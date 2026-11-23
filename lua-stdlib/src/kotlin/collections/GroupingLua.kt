package kotlin.collections

/**
 * Groups elements from the [Grouping] source by key and counts elements in each group.
 *
 * @return a [Map] associating the key of each group with the count of elements in the group.
 *
 * @sample samples.collections.Grouping.groupingByEachCount
 */
@SinceKotlin("1.1")
public actual fun <T, K> Grouping<T, K>.eachCount(): Map<K, Int> {
    val result = LinkedHashMap<K, Int>()
    val iter = sourceIterator()
    while (iter.hasNext()) {
        val key = keyOf(iter.next())
        val prev = result[key]
        result[key] = if (prev == null) 1 else prev + 1
    }
    return result.build()
}
