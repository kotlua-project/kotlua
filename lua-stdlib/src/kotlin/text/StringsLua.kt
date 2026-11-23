@file:OptIn(LuaIntrinsic::class)

package kotlin.text

import kotlin.lua.LuaIntrinsic
import kotlin.lua.luaEmptyTable
import kotlin.lua.luaTableSet

// --- From Strings.kt ---

public actual fun String.toUpperCase(): String = uppercase()

@SinceKotlin("1.5")
public actual fun String.uppercase(): String {
    if (isEmpty()) return this
    val sb = StringBuilder(length)
    var i = 0
    val n = length
    while (i < n) {
        sb.append(this[i].uppercase())
        i++
    }
    return sb.toString()
}

public actual fun String.toLowerCase(): String = lowercase()

@SinceKotlin("1.5")
public actual fun String.lowercase(): String {
    if (isEmpty()) return this
    val sb = StringBuilder(length)
    var i = 0
    val n = length
    while (i < n) {
        sb.append(this[i].lowercase())
        i++
    }
    return sb.toString()
}

public actual fun String.capitalize(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

public actual fun String.decapitalize(): String =
    replaceFirstChar { it.lowercase() }

@SinceKotlin("1.5")
public actual infix fun CharSequence?.contentEquals(other: CharSequence?): Boolean =
    contentEqualsImpl(other)

@SinceKotlin("1.5")
public actual fun CharSequence?.contentEquals(other: CharSequence?, ignoreCase: Boolean): Boolean =
    if (ignoreCase) contentEqualsIgnoreCaseImpl(other) else contentEqualsImpl(other)

// --- From TextH.kt (strings) ---

@SinceKotlin("1.2")
public actual fun String(chars: CharArray): String =
    chars.concatToString()

@SinceKotlin("1.2")
public actual fun String(chars: CharArray, offset: Int, length: Int): String {
    if (offset < 0 || length < 0 || offset > chars.size - length) {
        throw IndexOutOfBoundsException("offset: $offset, length: $length, size: ${chars.size}")
    }
    return chars.concatToString(offset, offset + length)
}

@SinceKotlin("1.4")
public actual fun CharArray.concatToString(): String =
    concatToString(0, size)

@SinceKotlin("1.4")
public actual fun CharArray.concatToString(startIndex: Int, endIndex: Int): String {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, size)
    if (startIndex == endIndex) return ""
    val buf: dynamic = luaEmptyTable()
    var k = 1
    var i = startIndex
    while (i < endIndex) {
        luaTableSet(buf, k, this[i].toString())
        k++
        i++
    }
    return LuaTable.concat(buf)
}

@SinceKotlin("1.4")
public actual fun String.toCharArray(): CharArray =
    toCharArray(0, length)

@SinceKotlin("1.4")
public actual fun String.toCharArray(startIndex: Int, endIndex: Int): CharArray {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, length)
    val len = endIndex - startIndex
    val result = CharArray(len)
    var i = 0
    while (i < len) {
        result[i] = this[startIndex + i]
        i++
    }
    return result
}

@SinceKotlin("2.0")
@IgnorableReturnValue
public actual fun String.toCharArray(
    destination: CharArray,
    destinationOffset: Int,
    startIndex: Int,
    endIndex: Int,
): CharArray {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, length)
    AbstractList.checkBoundsIndexes(destinationOffset, destinationOffset + endIndex - startIndex, destination.size)
    var dstIndex = destinationOffset
    var i = startIndex
    while (i < endIndex) {
        destination[dstIndex++] = this[i++]
    }
    return destination
}

internal actual fun String.nativeIndexOf(str: String, fromIndex: Int): Int {
    val start = if (fromIndex < 0) 0 else fromIndex
    val targetLen = str.length
    if (targetLen == 0) {
        return if (start <= length) start else length
    }
    val maxStart = length - targetLen
    if (start > maxStart) return -1
    val first = str[0]
    var i = start
    while (i <= maxStart) {
        if (this[i] == first) {
            var j = 1
            while (j < targetLen && this[i + j] == str[j]) {
                j++
            }
            if (j == targetLen) return i
        }
        i++
    }
    return -1
}

internal actual fun String.nativeLastIndexOf(str: String, fromIndex: Int): Int {
    if (fromIndex < 0) return -1
    val targetLen = str.length
    if (targetLen == 0) {
        return if (fromIndex <= length) fromIndex else length
    }
    val maxStart = length - targetLen
    if (maxStart < 0) return -1
    val first = str[0]
    var i = if (fromIndex > maxStart) maxStart else fromIndex
    while (i >= 0) {
        if (this[i] == first) {
            var j = 1
            while (j < targetLen && this[i + j] == str[j]) {
                j++
            }
            if (j == targetLen) return i
        }
        i--
    }
    return -1
}

internal actual fun String.nativeIndexOf(ch: Char, fromIndex: Int): Int {
    var i = if (fromIndex < 0) 0 else fromIndex
    val n = length
    while (i < n) {
        if (this[i] == ch) return i
        i++
    }
    return -1
}

internal actual fun String.nativeLastIndexOf(ch: Char, fromIndex: Int): Int {
    if (fromIndex < 0) return -1
    var i = if (fromIndex >= length) length - 1 else fromIndex
    while (i >= 0) {
        if (this[i] == ch) return i
        i--
    }
    return -1
}

public actual fun String.substring(startIndex: Int): String {
    AbstractList.checkPositionIndex(startIndex, length)
    if (startIndex == 0) return this
    if (startIndex == length) return ""
    return LuaString.sub(this, startIndex + 1)
}

public actual fun String.substring(startIndex: Int, endIndex: Int): String {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, length)
    if (startIndex == endIndex) return ""
    if (startIndex == 0 && endIndex == length) return this
    return LuaString.sub(this, startIndex + 1, endIndex)
}

public actual fun CharSequence.repeat(n: Int): String {
    require(n >= 0) { "Count 'n' must be non-negative, but was $n." }
    if (n == 0) return ""
    val s = toString()
    if (n == 1 || s.isEmpty()) return s
    return LuaString.rep(s, n)
}

public actual fun String.replace(oldChar: Char, newChar: Char, ignoreCase: Boolean): String {
    if (!ignoreCase && oldChar == newChar) return this
    val n = length
    if (n == 0) return this
    val sb = StringBuilder(n)
    var i = 0
    while (i < n) {
        val c = this[i]
        sb.append(if (c.equals(oldChar, ignoreCase)) newChar else c)
        i++
    }
    return sb.toString()
}

public actual fun String.replace(oldValue: String, newValue: String, ignoreCase: Boolean): String {
    var occurrenceIndex = indexOf(oldValue, 0, ignoreCase)
    if (occurrenceIndex < 0) return this
    val oldValueLength = oldValue.length
    val searchStep = oldValueLength.coerceAtLeast(1)
    val sb = StringBuilder(length)
    var i = 0
    do {
        sb.append(this, i, occurrenceIndex).append(newValue)
        i = occurrenceIndex + oldValueLength
        if (occurrenceIndex >= length) break
        occurrenceIndex = indexOf(oldValue, occurrenceIndex + searchStep, ignoreCase)
    } while (occurrenceIndex >= 0)
    if (i < length) {
        sb.append(this, i, length)
    }
    return sb.toString()
}

public actual fun String.replaceFirst(oldChar: Char, newChar: Char, ignoreCase: Boolean): String {
    val index = indexOf(oldChar, ignoreCase = ignoreCase)
    return if (index < 0) this else replaceRange(index, index + 1, newChar.toString())
}

public actual fun String.replaceFirst(oldValue: String, newValue: String, ignoreCase: Boolean): String {
    val index = indexOf(oldValue, ignoreCase = ignoreCase)
    return if (index < 0) this else replaceRange(index, index + oldValue.length, newValue)
}

public actual fun String?.equals(other: String?, ignoreCase: Boolean): Boolean {
    if (this === null) return other === null
    if (other === null) return false
    if (!ignoreCase) return this == other
    val n = length
    if (n != other.length) return false
    var i = 0
    while (i < n) {
        if (!this[i].equals(other[i], ignoreCase = true)) return false
        i++
    }
    return true
}

@SinceKotlin("1.2")
public actual fun String.compareTo(other: String, ignoreCase: Boolean): Int {
    if (!ignoreCase) return compareTo(other)
    val n1 = length
    val n2 = other.length
    val min = if (n1 < n2) n1 else n2
    var i = 0
    while (i < min) {
        var c1 = this[i]
        var c2 = other[i]
        if (c1 != c2) {
            c1 = c1.uppercaseChar()
            c2 = c2.uppercaseChar()
            if (c1 != c2) {
                c1 = c1.lowercaseChar()
                c2 = c2.lowercaseChar()
                if (c1 != c2) {
                    return c1.code - c2.code
                }
            }
        }
        i++
    }
    return n1 - n2
}

public actual fun String.startsWith(prefix: String, ignoreCase: Boolean): Boolean =
    startsWith(prefix, 0, ignoreCase)

public actual fun String.startsWith(prefix: String, startIndex: Int, ignoreCase: Boolean): Boolean =
    regionMatches(startIndex, prefix, 0, prefix.length, ignoreCase)

public actual fun String.endsWith(suffix: String, ignoreCase: Boolean): Boolean =
    regionMatches(length - suffix.length, suffix, 0, suffix.length, ignoreCase)

public actual fun CharSequence.regionMatches(
    thisOffset: Int,
    other: CharSequence,
    otherOffset: Int,
    length: Int,
    ignoreCase: Boolean,
): Boolean {
    if (length < 0) return false
    return regionMatchesImpl(thisOffset, other, otherOffset, length, ignoreCase)
}

@SinceKotlin("1.9")
public actual fun String.regionMatches(
    thisOffset: Int,
    other: String,
    otherOffset: Int,
    length: Int,
    ignoreCase: Boolean,
): Boolean {
    if (length < 0) return false
    return regionMatchesImpl(thisOffset, other, otherOffset, length, ignoreCase)
}

@SinceKotlin("1.2")
public actual val String.Companion.CASE_INSENSITIVE_ORDER: Comparator<String>
    get() = Comparator { a, b -> a.compareTo(b, ignoreCase = true) }

@SinceKotlin("1.4")
public actual fun String?.toBoolean(): Boolean =
    this != null && this.equals("true", ignoreCase = true)