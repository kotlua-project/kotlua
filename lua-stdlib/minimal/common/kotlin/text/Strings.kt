package kotlin.text

import kotlin.jvm.JvmName


/**
 * Returns a copy of this string converted to upper case using the rules of the default locale.
 */
@Deprecated("Use uppercase() instead.", ReplaceWith("uppercase()"))
@DeprecatedSinceKotlin(warningSince = "1.5", errorSince = "2.1")
public expect fun String.toUpperCase(): String

/**
 * Returns a copy of this string converted to upper case using Unicode mapping rules of the invariant locale.
 *
 * This function supports one-to-many and many-to-one character mapping,
 * thus the length of the returned string can be different from the length of the original string.
 *
 * @sample samples.text.Strings.uppercase
 */
@SinceKotlin("1.5")
public expect fun String.uppercase(): String

/**
 * Returns a copy of this string converted to lower case using the rules of the default locale.
 */
@Deprecated("Use lowercase() instead.", ReplaceWith("lowercase()"))
@DeprecatedSinceKotlin(warningSince = "1.5", errorSince = "2.1")
public expect fun String.toLowerCase(): String

/**
 * Returns a copy of this string converted to lower case using Unicode mapping rules of the invariant locale.
 *
 * This function supports one-to-many and many-to-one character mapping,
 * thus the length of the returned string can be different from the length of the original string.
 *
 * @sample samples.text.Strings.lowercase
 */
@SinceKotlin("1.5")
public expect fun String.lowercase(): String

/**
 * Returns a copy of this string having its first letter titlecased using the rules of the default locale,
 * or the original string if it's empty or already starts with a title case letter.
 *
 * The title case of a character is usually the same as its upper case with several exceptions.
 * The particular list of characters with the special title case form depends on the underlying platform.
 *
 * @sample samples.text.Strings.capitalize
 */
@Deprecated("Use replaceFirstChar instead.", ReplaceWith("replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }"))
@DeprecatedSinceKotlin(warningSince = "1.5")
public expect fun String.capitalize(): String

/**
 * Returns a copy of this string having its first letter lowercased using the rules of the default locale,
 * or the original string if it's empty or already starts with a lower case letter.
 *
 * @sample samples.text.Strings.decapitalize
 */
@Deprecated("Use replaceFirstChar instead.", ReplaceWith("replaceFirstChar { it.lowercase() }"))
@DeprecatedSinceKotlin(warningSince = "1.5")
public expect fun String.decapitalize(): String

/**
 * Returns `true` if the contents of this char sequence are equal to the contents of the specified [other],
 * i.e. both char sequences contain the same number of the same characters in the same order.
 *
 * @sample samples.text.Strings.contentEquals
 */
@SinceKotlin("1.5")
public expect infix fun CharSequence?.contentEquals(other: CharSequence?): Boolean

/**
 * Returns `true` if the contents of this char sequence are equal to the contents of the specified [other], optionally ignoring case difference.
 *
 * @param ignoreCase `true` to ignore character case when comparing contents.
 *
 * @sample samples.text.Strings.contentEquals
 */
@SinceKotlin("1.5")
public expect fun CharSequence?.contentEquals(other: CharSequence?, ignoreCase: Boolean): Boolean

/**
 * Returns a string having leading and trailing whitespace removed.
 */
@kotlin.internal.InlineOnly
public inline fun String.trim(): String = (this as CharSequence).trim().toString()

/**
 * Returns a subsequence of this char sequence having leading and trailing whitespace removed.
 */
public fun CharSequence.trim(): CharSequence = trim(Char::isWhitespace)

/**
 * Returns a subsequence of this char sequence having leading and trailing characters matching the [predicate] removed.
 */
public inline fun CharSequence.trim(predicate: (Char) -> Boolean): CharSequence {
    var startIndex = 0
    var endIndex = length - 1
    var startFound = false

    while (startIndex <= endIndex) {
        val index = if (!startFound) startIndex else endIndex
        val match = predicate(this[index])

        if (!startFound) {
            if (!match)
                startFound = true
            else
                startIndex += 1
        } else {
            if (!match)
                break
            else
                endIndex -= 1
        }
    }

    return subSequence(startIndex, endIndex + 1)
}

/**
 * Returns a copy of this string having its first character replaced with the result of the specified [transform],
 * or the original string if it's empty.
 *
 * @param transform function that takes the first character and returns the result of the transform applied to the character.
 *
 * @sample samples.text.Strings.replaceFirstChar
 */
@SinceKotlin("1.5")
@OptIn(kotlin.experimental.ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
@JvmName("replaceFirstCharWithCharSequence")
@kotlin.internal.InlineOnly
public inline fun String.replaceFirstChar(transform: (Char) -> CharSequence): String {
    return if (isNotEmpty()) transform(this[0]).toString() + substring(1) else this
}

/**
 * Returns `true` if this char sequence is not empty.
 *
 * @sample samples.text.Strings.stringIsNotEmpty
 */
@kotlin.internal.InlineOnly
public inline fun CharSequence.isNotEmpty(): Boolean = length > 0

internal fun CharSequence?.contentEqualsImpl(other: CharSequence?): Boolean {
    if (this is String && other is String) {
        return this == other
    }

    if (this === other) return true
    if (this == null || other == null || this.length != other.length) return false

    for (i in 0 until length) {
        if (this[i] != other[i]) {
            return false
        }
    }

    return true
}

internal fun CharSequence?.contentEqualsIgnoreCaseImpl(other: CharSequence?): Boolean {
    if (this is String && other is String) {
        return this.equals(other, ignoreCase = true)
    }

    if (this === other) return true
    if (this == null || other == null || this.length != other.length) return false

    for (i in 0 until length) {
        if (!this[i].equals(other[i], ignoreCase = true)) {
            return false
        }
    }

    return true
}

/**
 * Returns the index within this char sequence of the first occurrence of the specified [string],
 * starting from the specified [startIndex].
 *
 * @param ignoreCase `true` to ignore character case when matching a string. By default `false`.
 * @return An index of the first occurrence of [string] or `-1` if none is found.
 * @sample samples.text.Strings.indexOf
 */
public fun CharSequence.indexOf(string: String, startIndex: Int = 0, ignoreCase: Boolean = false): Int {
    return if (ignoreCase || this !is String)
        indexOf(string, startIndex, length, ignoreCase)
    else
        nativeIndexOf(string, startIndex)
}

private fun CharSequence.indexOf(other: CharSequence, startIndex: Int, endIndex: Int, ignoreCase: Boolean, last: Boolean = false): Int {
    val indices = if (!last)
        startIndex.coerceAtLeast(0)..endIndex.coerceAtMost(length)
    else
        startIndex.coerceAtMost(lastIndex) downTo endIndex.coerceAtLeast(0)

    if (this is String && other is String) { // smart cast
        for (index in indices) {
            if (other.regionMatches(0, this, index, other.length, ignoreCase))
                return index
        }
    } else {
        for (index in indices) {
            if (other.regionMatchesImpl(0, this, index, other.length, ignoreCase))
                return index
        }
    }
    return -1
}

/**
 * Implementation of [regionMatches] for CharSequences.
 * Invoked when it's already known that arguments are not Strings, so that no additional type checks are performed.
 */
internal fun CharSequence.regionMatchesImpl(thisOffset: Int, other: CharSequence, otherOffset: Int, length: Int, ignoreCase: Boolean): Boolean {
    if ((otherOffset < 0) || (thisOffset < 0) || (thisOffset > this.length - length) || (otherOffset > other.length - length)) {
        return false
    }

    for (index in 0 until length) {
        if (!this[thisOffset + index].equals(other[otherOffset + index], ignoreCase))
            return false
    }
    return true
}

/**
 * Returns the index of the last character in the char sequence or -1 if it is empty.
 */
public val CharSequence.lastIndex: Int
    get() = this.length - 1

/**
 * Returns the index within this string of the first occurrence of the specified character, starting from the specified [startIndex].
 *
 * @param ignoreCase `true` to ignore character case when matching a character. By default `false`.
 * @return An index of the first occurrence of [char] or -1 if none is found.
 */
public fun CharSequence.indexOf(char: Char, startIndex: Int = 0, ignoreCase: Boolean = false): Int {
    return if (ignoreCase || this !is String)
        indexOfAny(charArrayOf(char), startIndex, ignoreCase)
    else
        nativeIndexOf(char, startIndex)
}

/**
 * Finds the index of the first occurrence of any of the specified [chars] in this char sequence,
 * starting from the specified [startIndex] and optionally ignoring the case.
 *
 * @param ignoreCase `true` to ignore character case when matching a character. By default `false`.
 * @return An index of the first occurrence of matched character from [chars] or -1 if none of [chars] are found.
 *
 */
public fun CharSequence.indexOfAny(chars: CharArray, startIndex: Int = 0, ignoreCase: Boolean = false): Int {
    if (!ignoreCase && chars.size == 1 && this is String) {
        val char = chars.single()
        return nativeIndexOf(char, startIndex)
    }

    for (index in startIndex.coerceAtLeast(0)..lastIndex) {
        val charAtIndex = get(index)
        if (chars.any { it.equals(charAtIndex, ignoreCase) })
            return index
    }
    return -1
}

/**
 * Replaces the part of the string at the given range with the [replacement] char sequence.
 * @param startIndex the index of the first character to be replaced.
 * @param endIndex the index of the first character after the replacement to keep in the string.
 *
 * @sample samples.text.Strings.replaceRange
 */
@kotlin.internal.InlineOnly
public inline fun String.replaceRange(startIndex: Int, endIndex: Int, replacement: CharSequence): String =
    (this as CharSequence).replaceRange(startIndex, endIndex, replacement).toString()

/**
 * Returns a char sequence with content of this char sequence where its part at the given range
 * is replaced with the [replacement] char sequence.
 * @param startIndex the index of the first character to be replaced.
 * @param endIndex the index of the first character after the replacement to keep in the string.
 *
 * @sample samples.text.Strings.replaceRange
 */
public fun CharSequence.replaceRange(startIndex: Int, endIndex: Int, replacement: CharSequence): CharSequence {
    if (endIndex < startIndex)
        throw IndexOutOfBoundsException("End index ($endIndex) is less than start index ($startIndex).")
    val sb = StringBuilder()
    sb.appendRange(this, 0, startIndex)
    sb.append(replacement)
    sb.appendRange(this, endIndex, length)
    return sb
}

