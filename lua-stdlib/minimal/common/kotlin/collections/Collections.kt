package kotlin.collections

import kotlin.collections.isNotEmpty
import kotlin.random.Random

/**
 * Returns `true` if collection has at least one element.
 *
 * @sample samples.collections.Collections.Aggregates.any
 */
public fun <T> Iterable<T>.any(): Boolean {
    if (this is Collection) return !isEmpty()
    return iterator().hasNext()
}

/**
 * Returns `true` if at least one element matches the given [predicate].
 *
 * @sample samples.collections.Collections.Aggregates.anyWithPredicate
 */
public inline fun <T> Iterable<T>.any(predicate: (T) -> Boolean): Boolean {
    if (this is Collection && isEmpty()) return false
    for (element in this) if (predicate(element)) return true
    return false
}

/**
 * Returns `true` if all elements match the given [predicate].
 *
 * Note that if the collection contains no elements, the function returns `true`
 * because there are no elements in it that _do not_ match the predicate.
 * See a more detailed explanation of this logic concept in ["Vacuous truth"](https://en.wikipedia.org/wiki/Vacuous_truth) article.
 *
 * @sample samples.collections.Collections.Aggregates.all
 */
public inline fun <T> Iterable<T>.all(predicate: (T) -> Boolean): Boolean {
    if (this is Collection && isEmpty()) return true
    for (element in this) if (!predicate(element)) return false
    return true
}

/**
 * Creates a string from all the elements separated using [separator] and using the given [prefix] and [postfix] if supplied.
 *
 * If the collection has no elements, the result consists of only [prefix] followed by [postfix] (both are empty by default).
 *
 * If the collection is huge, you can specify a non-negative value of [limit], in which case only the first [limit]
 * elements will be appended, followed by the [truncated] string (which defaults to "...").
 *
 * @sample samples.collections.Collections.Transformations.joinToString
 */
public fun <T> Iterable<T>.joinToString(separator: CharSequence = ", ", prefix: CharSequence = "", postfix: CharSequence = "", limit: Int = -1, truncated: CharSequence = "...", transform: ((T) -> CharSequence)? = null): String {
    return joinTo(StringBuilder(), separator, prefix, postfix, limit, truncated, transform).toString()
}

public fun <T, A : Appendable> Iterable<T>.joinTo(
    buffer: A,
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "...",
    transform: ((T) -> CharSequence)? = null,
): A {
    buffer.append(prefix)
    var count = 0
    for (element in this) {
        if (++count > 1) buffer.append(separator)
        if (limit < 0 || count <= limit) {
            when {
                transform != null -> buffer.append(transform(element))
                element is CharSequence? -> buffer.append(element)
                element is Char -> buffer.append(element)
                else -> buffer.append(element.toString())
            }
        } else {
            break
        }
    }
    if (limit >= 0 && count > limit) buffer.append(truncated)
    buffer.append(postfix)
    return buffer
}

/**
 * Returns index of the first element matching the given [predicate], or -1 if the list does not contain such element.
 */
public inline fun <T> List<T>.indexOfFirst(predicate: (T) -> Boolean): Int {
    var index = 0
    for (item in this) {
        if (predicate(item))
            return index
        index++
    }
    return -1
}

/**
 * Returns index of the last element matching the given [predicate], or -1 if the list does not contain such element.
 */
public inline fun <T> List<T>.indexOfLast(predicate: (T) -> Boolean): Int {
    val iterator = this.listIterator(size)
    while (iterator.hasPrevious()) {
        if (predicate(iterator.previous())) {
            return iterator.nextIndex()
        }
    }
    return -1
}

/**
 * Returns a new [MutableList] filled with all elements of this collection.
 */
public fun <T> Iterable<T>.toMutableList(): MutableList<T> {
    if (this is Collection<T>)
        return this.toMutableList()
    return toCollection(ArrayList<T>())
}

/**
 * Returns a new [MutableList] filled with all elements of this collection.
 */
public fun <T> Collection<T>.toMutableList(): MutableList<T> {
    return ArrayList(this)
}

/**
 * Appends all elements to the given [destination] collection.
 */
@IgnorableReturnValue
public fun <T, C : MutableCollection<in T>> Iterable<T>.toCollection(destination: C): C {
    for (item in this) {
        destination.add(item)
    }
    return destination
}

/**
 * Randomly shuffles elements in this list in-place using the specified [random] instance as the source of randomness.
 *
 * See: [A modern version of Fisher-Yates shuffle algorithm](https://en.wikipedia.org/wiki/Fisher%E2%80%93Yates_shuffle#The_modern_algorithm).
 */
@SinceKotlin("1.3")
public fun <T> MutableList<T>.shuffle(random: Random): Unit {
    for (i in lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        this[j] = this.set(i, this[j])
    }
}

/**
 * Removes all elements from this [MutableIterable] that match the given [predicate].
 *
 * @return `true` if any element was removed from this collection, or `false` when no elements were removed and collection was not modified.
 */
@IgnorableReturnValue
public fun <T> MutableIterable<T>.removeAll(predicate: (T) -> Boolean): Boolean = filterInPlace(predicate, true)

/**
 * Retains only elements of this [MutableIterable] that match the given [predicate].
 *
 * @return `true` if any element was removed from this collection, or `false` when all elements were retained and collection was not modified.
 */
@IgnorableReturnValue
public fun <T> MutableIterable<T>.retainAll(predicate: (T) -> Boolean): Boolean = filterInPlace(predicate, false)

private fun <T> MutableIterable<T>.filterInPlace(predicate: (T) -> Boolean, predicateResultToRemove: Boolean): Boolean {
    var result = false
    with(iterator()) {
        while (hasNext())
            if (predicate(next()) == predicateResultToRemove) {
                remove()
                result = true
            }
    }
    return result
}

/**
 * Returns the first element matching the given [predicate], or `null` if element was not found.
 */
public inline fun <T> Iterable<T>.firstOrNull(predicate: (T) -> Boolean): T? {
    for (element in this) if (predicate(element)) return element
    return null
}

/**
 * Populates and returns the [destination] mutable map with key-value pairs,
 * where key is provided by the [keySelector] function and
 * and value is provided by the [valueTransform] function applied to elements of the given collection.
 *
 * If any two elements would have the same key returned by [keySelector] the last one gets added to the map.
 *
 * @sample samples.collections.Collections.Transformations.associateByToWithValueTransform
 */
@IgnorableReturnValue
public inline fun <T, K, V, M : MutableMap<in K, in V>> Iterable<T>.associateByTo(destination: M, keySelector: (T) -> K, valueTransform: (T) -> V): M {
    for (element in this) {
        destination.put(keySelector(element), valueTransform(element))
    }
    return destination
}

/**
 * Converts this [Iterable] to a list if it is not a [Collection].
 * Otherwise, returns this.
 */
internal fun <T> Iterable<T>.convertToListIfNotCollection(): Collection<T> =
    if (this is Collection) this else toList()

/**
 * Removes all elements from this [MutableCollection] that are also contained in the given [elements] collection.
 */
@IgnorableReturnValue
public fun <T> MutableCollection<in T>.removeAll(elements: Iterable<T>): Boolean {
    return removeAll(elements.convertToListIfNotCollection())
}

/**
 * Removes all elements from this [MutableCollection] that are also contained in the given [elements] sequence.
 */
@IgnorableReturnValue
public fun <T> MutableCollection<in T>.removeAll(elements: Sequence<T>): Boolean {
    val list = elements.toList()
    return list.isNotEmpty() && removeAll(list)
}

/**
 * Removes all elements from this [MutableCollection] that are also contained in the given [elements] array.
 */
@IgnorableReturnValue
public fun <T> MutableCollection<in T>.removeAll(elements: Array<out T>): Boolean {
    return elements.isNotEmpty() && removeAll(elements.asList())
}

/**
 * Returns a [List] containing all elements.
 */
public fun <T> Iterable<T>.toList(): List<T> {
    if (this is Collection) {
        return when (size) {
            0 -> emptyList()
            1 -> listOf(if (this is List) get(0) else iterator().next())
            else -> this.toMutableList()
        }
    }
    return this.toMutableList().optimizeReadOnlyList()
}

/**
 * Returns a [List] containing all elements.
 *
 * The operation is _terminal_.
 */
public fun <T> Sequence<T>.toList(): List<T> {
    val it = iterator()
    if (!it.hasNext())
        return emptyList()
    val element = it.next()
    if (!it.hasNext())
        return listOf(element)
    val dst = ArrayList<T>()
    dst.add(element)
    while (it.hasNext()) dst.add(it.next())
    return dst
}


