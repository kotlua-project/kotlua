package kotlin.collections

inline fun <T, R> Array<T>.fold(acc: R, fn: (T, R) -> R): R {
    TODO()
}

/**
 * Returns `true` if the array is empty.
 */
@kotlin.internal.InlineOnly
public inline fun <T> Array<out T>.isEmpty(): Boolean {
    return size == 0
}

/**
 * Returns `true` if [element] is found in the array.
 */
public operator fun <@kotlin.internal.OnlyInputTypes T> Array<out T>.contains(element: T): Boolean {
    return indexOf(element) >= 0
}

/**
 * Returns first index of [element], or -1 if the array does not contain element.
 */
public fun <@kotlin.internal.OnlyInputTypes T> Array<out T>.indexOf(element: T): Int {
    if (element == null) {
        for (index in indices) {
            if (this[index] == null) {
                return index
            }
        }
    } else {
        for (index in indices) {
            if (element == this[index]) {
                return index
            }
        }
    }
    return -1
}

/**
 * Returns the range of valid indices for the array.
 */
public val <T> Array<out T>.indices: IntRange
    get() = IntRange(0, lastIndex)

/**
 * Returns the last valid index for the array.
 */
public val <T> Array<out T>.lastIndex: Int
    get() = size - 1

/**
 * Returns a list containing all elements that are not `null`.
 *
 * @sample samples.collections.Collections.Filtering.filterNotNull
 */
public fun <T : Any> Array<out T?>.filterNotNull(): List<T> {
    return filterNotNullTo(ArrayList<T>())
}

/**
 * Appends all elements that are not `null` to the given [destination].
 *
 * @sample samples.collections.Collections.Filtering.filterNotNullTo
 */
@IgnorableReturnValue
public fun <C : MutableCollection<in T>, T : Any> Array<out T?>.filterNotNullTo(destination: C): C {
    for (element in this) if (element != null) destination.add(element)
    return destination
}

/**
 * Returns an element at the given [index] or `null` if the [index] is out of bounds of this array.
 *
 * @sample samples.collections.Collections.Elements.getOrNull
 */
public fun <T> Array<out T>.getOrNull(index: Int): T? {
    return if (index in indices) get(index) else null
}

/**
 * Returns `true` if [element] is found in the array.
 */
public operator fun CharArray.contains(element: Char): Boolean {
    return indexOf(element) >= 0
}

/**
 * Returns first index of [element], or -1 if the array does not contain element.
 */
public fun CharArray.indexOf(element: Char): Int {
    for (index in indices) {
        if (element == this[index]) {
            return index
        }
    }
    return -1
}

/**
 * Returns the range of valid indices for the array.
 */
public val CharArray.indices: IntRange
    get() = IntRange(0, lastIndex)

/**
 * Returns the last valid index for the array.
 */
public val CharArray.lastIndex: Int
    get() = size - 1

/**
 * Returns the single element, or throws an exception if the array is empty or has more than one element.
 */
public fun CharArray.single(): Char {
    return when (size) {
        0 -> throw NoSuchElementException("Array is empty.")
        1 -> this[0]
        else -> throw IllegalArgumentException("Array has more than one element.")
    }
}

/**
 * Returns `true` if at least one element matches the given [predicate].
 *
 * @sample samples.collections.Collections.Aggregates.anyWithPredicate
 */
public inline fun CharArray.any(predicate: (Char) -> Boolean): Boolean {
    for (element in this) if (predicate(element)) return true
    return false
}

/**
 * Returns `true` if the array is not empty.
 */
@kotlin.internal.InlineOnly
public inline fun <T> Array<out T>.isNotEmpty(): Boolean {
    return !isEmpty()
}
/**
 * Returns a [Set] of all elements.
 *
 * The returned set preserves the element iteration order of the original array.
 */
public fun <T> Array<out T>.toSet(): Set<T> {
    return when (size) {
        0 -> emptySet()
        1 -> setOf(this[0])
        else -> toCollection(LinkedHashSet<T>(mapCapacity(size)))
    }
}

/**
 * Appends all elements to the given [destination] collection.
 */
@IgnorableReturnValue
public fun <T, C : MutableCollection<in T>> Array<out T>.toCollection(destination: C): C {
    for (item in this) {
        destination.add(item)
    }
    return destination
}
