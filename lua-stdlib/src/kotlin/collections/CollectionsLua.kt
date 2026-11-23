@file:OptIn(LuaIntrinsic::class)

package kotlin.collections

import kotlin.random.Random

@PublishedApi
internal fun <T> arrayToArrayList(array: Array<out T>): ArrayList<T> {
    val copy: dynamic = luaEmptyTable()
    val n = array.size
    var i = 0
    while (i < n) {
        luaTableSet(copy, i + 1, array[i])
        i++
    }
    return ArrayList(Array<Any?>(copy, n, true))
}

@kotlin.internal.InlineOnly
internal actual inline fun <T> Array<out T>.asArrayList(): ArrayList<T> = arrayToArrayList(this)

/**
 * Returns a new read-only list containing only the specified object [element].
 *
 * The returned list is serializable (JVM).
 *
 * @sample samples.collections.Collections.Lists.singletonReadOnlyList
 */
@SinceKotlin("1.9")
@Suppress("UNCHECKED_CAST")
public actual fun <T> listOf(element: T): List<T> =
    ArrayList<Any?>(1).apply { add(element) }.build() as List<T>

@PublishedApi
@SinceKotlin("1.3")
@kotlin.internal.InlineOnly
internal actual inline fun <E> buildListInternal(builderAction: MutableList<E>.() -> Unit): List<E> =
    ArrayList<E>().apply(builderAction).build()

@PublishedApi
@SinceKotlin("1.3")
@kotlin.internal.InlineOnly
internal actual inline fun <E> buildListInternal(capacity: Int, builderAction: MutableList<E>.() -> Unit): List<E> =
    ArrayList<E>(capacity).apply(builderAction).build()

@PublishedApi
@SinceKotlin("1.3")
@IgnorableReturnValue
internal actual fun checkIndexOverflow(index: Int): Int {
    if (index < 0) {
        throwIndexOverflow()
    }
    return index
}

@PublishedApi
@SinceKotlin("1.3")
@IgnorableReturnValue
internal actual fun checkCountOverflow(count: Int): Int {
    if (count < 0) {
        throwCountOverflow()
    }
    return count
}

/**
 * In JVM if the size of [array] is bigger than [collectionSize], sets `array[collectionSize] = null`.
 * In other platforms does nothing.
 * Returns the given [array].
 */
internal actual fun <T> terminateCollectionToArray(collectionSize: Int, array: Array<T>): Array<T> = array

/**
 * Returns the array if it's not `null`, or an empty array otherwise.
 * @sample samples.collections.Arrays.Usage.arrayOrEmpty
 */
public actual inline fun <reified T> Array<out T>?.orEmpty(): Array<out T> = this ?: emptyArray()

/**
 * Returns a *typed* array containing all the elements of this collection.
 *
 * Allocates an array of runtime type `T` having its size equal to the size of this collection
 * and populates the array with the elements of this collection.
 * @sample samples.collections.Collections.Collections.collectionToTypedArray
 */
public actual inline fun <reified T> Collection<T>.toTypedArray(): Array<T> {
    @Suppress("UNCHECKED_CAST")
    return collectionToArray(this) as Array<T>
}

/**
 * Fills the list with the provided [value].
 *
 * Each element in the list gets replaced with the [value].
 */
@SinceKotlin("1.2")
public actual fun <T> MutableList<T>.fill(value: T): Unit {
    for (index in 0..lastIndex) {
        this[index] = value
    }
}

/**
 * Randomly shuffles elements in this list in-place.
 *
 * See: [A modern version of Fisher-Yates shuffle algorithm](https://en.wikipedia.org/wiki/Fisher%E2%80%93Yates_shuffle#The_modern_algorithm).
 */
@SinceKotlin("1.2")
public actual fun <T> MutableList<T>.shuffle(): Unit {
    shuffle(Random)
}

/**
 * Returns a new list with the elements of this collection randomly shuffled.
 */
@SinceKotlin("1.2")
public actual fun <T> Iterable<T>.shuffled(): List<T> = toMutableList().apply { shuffle() }

/**
 * Sorts elements in the list in-place according to their natural sort order.
 *
 * The sort is _stable_. It means that equal elements preserve their order relative to each other after sorting.
 *
 * @sample samples.collections.Collections.Sorting.sortMutableList
 */
public actual fun <T : Comparable<T>> MutableList<T>.sort(): Unit {
    collectionsSort(this, naturalOrder())
}

/**
 * Sorts elements in the list in-place according to the order specified with [comparator].
 *
 * The sort is _stable_. It means that equal elements preserve their order relative to each other after sorting.
 *
 * @sample samples.collections.Collections.Sorting.sortMutableListWith
 */
public actual fun <T> MutableList<T>.sortWith(comparator: Comparator<in T>): Unit {
    collectionsSort(this, comparator)
}

private fun <T> collectionsSort(list: MutableList<T>, comparator: Comparator<in T>) {
    val n = list.size
    if (n <= 1) return
    @Suppress("UNCHECKED_CAST")
    val array = collectionToArray(list) as Array<T>
    sortArrayWith(array.storage, n, 0, n, comparator)
    var i = 0
    while (i < n) {
        list[i] = array[i]
        i++
    }
}

@PublishedApi
internal actual fun collectionToArray(collection: Collection<*>): Array<Any?> {
    val table: dynamic = luaEmptyTable()
    var count = 0
    for (element in collection) {
        count++
        luaTableSet(table, count, element)
    }
    return Array(table, count, true)
}

internal actual fun <T> collectionToArray(collection: Collection<*>, array: Array<T>): Array<T> {
    if (collection.size > array.size) {
        @Suppress("UNCHECKED_CAST")
        return collectionToArray(collection) as Array<T>
    }
    var index = 0
    for (element in collection) {
        luaTableSet(array.storage, index + 1, element)
        index++
    }
    return terminateCollectionToArray(index, array)
}

internal actual fun <T> arrayOfNulls(reference: Array<T>, size: Int): Array<T> {
    return arrayOfNulls<Any>(size).unsafeCast<Array<T>>()
}

internal actual fun <T> Array<out T>.copyToArrayOfAny(isVarargs: Boolean): Array<out Any?> {
    return if (isVarargs) {
        this as Array<out Any?>
    } else {
        unsafeCast<Array<Any?>>().copyOf()
    }
}

internal actual fun <K, V> Map<K, V>.toSingletonMapOrSelf(): Map<K, V> = toSingletonMap()

internal actual fun <K, V> Map<out K, V>.toSingletonMap(): Map<K, V> =
    with(entries.iterator().next()) { mapOf(key to value) }
