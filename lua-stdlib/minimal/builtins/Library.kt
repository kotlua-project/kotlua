@file:Suppress("NON_MEMBER_FUNCTION_NO_BODY", "REIFIED_TYPE_PARAMETER_NO_INLINE")

package kotlin

/**
 * Returns a string representation of the object. Can be called with a null receiver, in which case
 * it returns the string "null".
 */
public actual fun Any?.toString(): String = this?.toString() ?: "null"

/**
 * Concatenates this string with the string representation of the given [other] object. If either the receiver
 * or the [other] object are null, they are represented as the string "null".
 *
 * @sample samples.text.Strings.nullableStringPlus
 */
public actual operator fun String?.plus(other: Any?): String =
    (this ?: "null").plus(other?.toString() ?: "null")

/**
 * Returns an array of objects of the given type with the given [size], initialized with null values.
 *
 * @throws RuntimeException if the specified [size] is negative.
 */
public actual fun <reified T> arrayOfNulls(size: Int): Array<T?> = Array<T?>(size)

/**
 * Returns an array containing the specified elements.
 *
 * @sample samples.collections.Arrays.Constructors.arrayOfSample
 */
public actual inline fun <reified T> arrayOf(vararg elements: T): Array<T> = elements.unsafeCast<Array<T>>()

/**
 * Returns an array containing enum entries of the enum type [T].
 *
 * The function returns a new instance of the array on every call.
 * The array could be mutated, so working with it may also require defensive copying.
 * Consider using [kotlin.enums.enumEntries] as a more efficient alternative
 * returning an immutable list of enum entries.
 *
 * @see kotlin.enums.enumEntries
 */
@SinceKotlin("1.1")
public actual inline fun <reified T : Enum<T>> enumValues(): Array<T>

/**
 * Returns the enum entry of type [T] with the specified [name].
 *
 * The [name] must exactly match an existing enum constant of type [T] (case-sensitive).
 *
 * @throws IllegalArgumentException if no enum constant with the specified [name] exists in [T].
 * @sample samples.misc.Enums.enumValueOfSample
 */
@SinceKotlin("1.1")
public actual inline fun <reified T : Enum<T>> enumValueOf(name: String): T
