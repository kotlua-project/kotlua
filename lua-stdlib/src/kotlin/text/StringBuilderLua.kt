@file:OptIn(LuaIntrinsic::class)

package kotlin.text

import kotlin.lua.LuaTable

/**
 * A mutable sequence of characters.
 *
 * String builder can be used to efficiently perform multiple string manipulation operations.
 */
actual class StringBuilder actual constructor(content: String) : Appendable, CharSequence {
    /** Constructs an empty string builder. */
    actual constructor() : this("")

    /** Constructs an empty string builder with the specified initial [capacity]. */
    actual constructor(capacity: Int) : this("")

    /** Constructs a string builder that contains the same characters as the specified [content] char sequence. */
    actual constructor(content: CharSequence) : this(content.toString())

    private var parts: dynamic = luaEmptyTable()
    private var partCount: Int = 0
    private var _length: Int = 0

    init {
        val len = content.length
        if (len > 0) {
            luaTableSet(parts, 1, content)
            partCount = 1
            _length = len
        }
    }

    internal fun appendChunk(chunk: String) {
        val len = chunk.length
        if (len > 0) {
            partCount++
            luaTableSet(parts, partCount, chunk)
            _length += len
        }
    }

    @IgnorableReturnValue
    internal fun materialize(): String {
        if (_length == 0) return ""
        if (partCount == 1) {
            return luaTableGet(parts, 1).unsafeCast<String>()
        }
        val result = LuaTable.concat(parts)
        parts = luaEmptyTable()
        luaTableSet(parts, 1, result)
        partCount = 1
        return result
    }

    private fun replaceWith(s: String) {
        parts = luaEmptyTable()
        val len = s.length
        if (len > 0) {
            luaTableSet(parts, 1, s)
            partCount = 1
            _length = len
        } else {
            partCount = 0
            _length = 0
        }
    }

    actual override val length: Int
        get() = _length

    actual override operator fun get(index: Int): Char {
        AbstractList.checkElementIndex(index, _length)
        return materialize()[index]
    }

    actual override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        substring(startIndex, endIndex)

    @IgnorableReturnValue
    actual override fun append(value: Char): StringBuilder {
        appendChunk(value.toString())
        return this
    }

    @IgnorableReturnValue
    actual override fun append(value: CharSequence?): StringBuilder {
        appendChunk(value?.toString() ?: "null")
        return this
    }

    @IgnorableReturnValue
    actual override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): StringBuilder =
        appendRange(value ?: "null", startIndex, endIndex)

    /**
     * Reverses the contents of this string builder and returns this instance.
     *
     * Surrogate pairs included in this string builder are treated as single characters.
     * Therefore, the order of the high-low surrogates is never reversed.
     *
     * Note that the reverse operation may produce new surrogate pairs that were unpaired low-surrogates and high-surrogates before the operation.
     * For example, reversing `"\uDC00\uD800"` produces `"\uD800\uDC00"` which is a valid surrogate pair.
     */
    @IgnorableReturnValue
    public actual fun reverse(): StringBuilder {
        if (_length <= 1) return this
        val s = materialize()
        val buf: dynamic = luaEmptyTable()
        var k = 1
        var i = s.length - 1
        while (i >= 0) {
            var start = i
            while (start > 0 && (LuaString.byte(s, start + 1) and 0xC0) == 0x80) {
                start--
            }
            luaTableSet(buf, k++, LuaString.sub(s, start + 1, i + 1))
            i = start - 1
        }
        replaceWith(LuaTable.concat(buf))
        return this
    }

    /**
     * Appends the string representation of the specified object [value] to this string builder and returns this instance.
     *
     * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
     * and then that string was appended to this string builder.
     */
    @IgnorableReturnValue
    public actual fun append(value: Any?): StringBuilder {
        appendChunk(value.toString())
        return this
    }

    /**
     * Appends the string representation of the specified boolean [value] to this string builder and returns this instance.
     *
     * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
     * and then that string was appended to this string builder.
     */
    @SinceKotlin("1.3")
    @IgnorableReturnValue
    public actual fun append(value: Boolean): StringBuilder {
        appendChunk(value.toString())
        return this
    }

    /**
     * Appends the string representation of the specified int [value] to this string builder and returns this instance.
     *
     * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
     * and then that string was appended to this string builder.
     */
    @SinceKotlin("1.9")
    @IgnorableReturnValue
    public actual fun append(value: Int): StringBuilder {
        appendChunk(value.toString())
        return this
    }

    /**
     * Appends the string representation of the specified long [value] to this string builder and returns this instance.
     *
     * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
     * and then that string was appended to this string builder.
     */
    @SinceKotlin("1.9")
    @IgnorableReturnValue
    public actual fun append(value: Long): StringBuilder {
        appendChunk(value.toString())
        return this
    }

    /**
     * Appends the string representation of the specified float [value] to this string builder and returns this instance.
     *
     * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
     * and then that string was appended to this string builder.
     */
    @SinceKotlin("1.9")
    @IgnorableReturnValue
    public actual fun append(value: Float): StringBuilder {
        appendChunk(value.toString())
        return this
    }

    /**
     * Appends the string representation of the specified double [value] to this string builder and returns this instance.
     *
     * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
     * and then that string was appended to this string builder.
     */
    @SinceKotlin("1.9")
    @IgnorableReturnValue
    actual fun append(value: Double): StringBuilder {
        appendChunk(value.toString())
        return this
    }

    /**
     * Appends characters in the specified character array [value] to this string builder and returns this instance.
     *
     * Characters are appended in order, starting at the index 0.
     */
    @SinceKotlin("1.4")
    @IgnorableReturnValue
    actual fun append(value: CharArray): StringBuilder {
        appendChunk(charArraySliceToString(value, 0, value.size))
        return this
    }

    /**
     * Appends the specified string [value] to this string builder and returns this instance.
     *
     * If [value] is `null`, then the four characters `"null"` are appended.
     */
    @SinceKotlin("1.3")
    @IgnorableReturnValue
    actual fun append(value: String?): StringBuilder {
        appendChunk(value ?: "null")
        return this
    }

    /**
     * Returns the current capacity of this string builder.
     *
     * The capacity is the maximum length this string builder can have before an allocation occurs.
     *
     * In Kotlin/JS implementation of StringBuilder the value returned from this method may not indicate the actual size of the backing storage.
     */
    @SinceKotlin("1.3")
    actual fun capacity(): Int = _length

    /**
     * Ensures that the capacity of this string builder is at least equal to the specified [minimumCapacity].
     *
     * If the current capacity is less than the [minimumCapacity], a new backing storage is allocated with greater capacity.
     * Otherwise, this method takes no action and simply returns.
     */
    @SinceKotlin("1.4")
    actual fun ensureCapacity(minimumCapacity: Int) {}

    /**
     * Returns the index within this string builder of the first occurrence of the specified [string].
     *
     * Returns `-1` if the specified [string] does not occur in this string builder.
     */
    @SinceKotlin("1.4")
    actual fun indexOf(string: String): Int = indexOf(string, 0)

    /**
     * Returns the index within this string builder of the first occurrence of the specified [string],
     * starting at the specified [startIndex].
     *
     * Returns `-1` if the specified [string] does not occur in this string builder starting at the specified [startIndex].
     */
    @SinceKotlin("1.4")
    public actual fun indexOf(string: String, startIndex: Int): Int {
        val start = if (startIndex < 0) 0 else startIndex
        val targetLen = string.length
        if (targetLen == 0) {
            return if (start <= _length) start else _length
        }
        val maxStart = _length - targetLen
        if (start > maxStart) return -1
        val s = materialize()
        val first = string[0]
        var i = start
        while (i <= maxStart) {
            if (s[i] == first) {
                var j = 1
                while (j < targetLen && s[i + j] == string[j]) {
                    j++
                }
                if (j == targetLen) return i
            }
            i++
        }
        return -1
    }

    /**
     * Returns the index within this string builder of the last occurrence of the specified [string].
     * The last occurrence of empty string `""` is considered to be at the index equal to `this.length`.
     *
     * Returns `-1` if the specified [string] does not occur in this string builder.
     */
    @SinceKotlin("1.4")
    actual fun lastIndexOf(string: String): Int = lastIndexOf(string, _length)

    /**
     * Returns the index within this string builder of the last occurrence of the specified [string],
     * starting from the specified [startIndex] toward the beginning.
     *
     * Returns `-1` if the specified [string] does not occur in this string builder starting at the specified [startIndex].
     */
    @SinceKotlin("1.4")
    public actual fun lastIndexOf(string: String, startIndex: Int): Int {
        if (startIndex < 0) return -1
        val targetLen = string.length
        if (targetLen == 0) {
            return if (startIndex <= _length) startIndex else _length
        }
        val maxStart = _length - targetLen
        if (maxStart < 0) return -1
        val s = materialize()
        val first = string[0]
        var i = if (startIndex > maxStart) maxStart else startIndex
        while (i >= 0) {
            if (s[i] == first) {
                var j = 1
                while (j < targetLen && s[i + j] == string[j]) {
                    j++
                }
                if (j == targetLen) return i
            }
            i--
        }
        return -1
    }

    /**
     * Inserts the string representation of the specified boolean [value] into this string builder at the specified [index] and returns this instance.
     *
     * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
     * and then that string was inserted into this string builder at the specified [index].
     *
     * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
     */
    @SinceKotlin("1.4")
    @IgnorableReturnValue
    actual fun insert(index: Int, value: Boolean): StringBuilder =
        insert(index, value.toString())

    /**
     * Inserts the string representation of the specified int [value] into this string builder at the specified [index] and returns this instance.
     *
     * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
     * and then that string was inserted into this string builder at the specified [index].
     *
     * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
     */
    @SinceKotlin("1.9")
    @IgnorableReturnValue
    actual fun insert(index: Int, value: Int): StringBuilder =
        insert(index, value.toString())

    /**
     * Inserts the string representation of the specified long [value] into this string builder at the specified [index] and returns this instance.
     *
     * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
     * and then that string was inserted into this string builder at the specified [index].
     *
     * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
     */
    @SinceKotlin("1.9")
    @IgnorableReturnValue
    actual fun insert(index: Int, value: Long): StringBuilder =
        insert(index, value.toString())

    /**
     * Inserts the string representation of the specified float [value] into this string builder at the specified [index] and returns this instance.
     *
     * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
     * and then that string was inserted into this string builder at the specified [index].
     *
     * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
     */
    @SinceKotlin("1.9")
    @IgnorableReturnValue
    actual fun insert(index: Int, value: Float): StringBuilder =
        insert(index, value.toString())

    /**
     * Inserts the string representation of the specified double [value] into this string builder at the specified [index] and returns this instance.
     *
     * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
     * and then that string was inserted into this string builder at the specified [index].
     *
     * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
     */
    @SinceKotlin("1.9")
    @IgnorableReturnValue
    actual fun insert(index: Int, value: Double): StringBuilder =
        insert(index, value.toString())

    /**
     * Inserts the specified character [value] into this string builder at the specified [index] and returns this instance.
     *
     * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
     */
    @SinceKotlin("1.4")
    @IgnorableReturnValue
    actual fun insert(index: Int, value: Char): StringBuilder =
        insert(index, value.toString())

    /**
     * Inserts characters in the specified character array [value] into this string builder at the specified [index] and returns this instance.
     *
     * The inserted characters go in same order as in the [value] character array, starting at [index].
     *
     * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
     */
    @SinceKotlin("1.4")
    @IgnorableReturnValue
    actual fun insert(index: Int, value: CharArray): StringBuilder {
        AbstractList.checkPositionIndex(index, _length)
        return insert(index, charArraySliceToString(value, 0, value.size))
    }

    /**
     * Inserts characters in the specified character sequence [value] into this string builder at the specified [index] and returns this instance.
     *
     * The inserted characters go in the same order as in the [value] character sequence, starting at [index].
     *
     * @param index the position in this string builder to insert at.
     * @param value the character sequence from which characters are inserted. If [value] is `null`, then the four characters `"null"` are inserted.
     *
     * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
     */
    @SinceKotlin("1.4")
    @IgnorableReturnValue
    actual fun insert(index: Int, value: CharSequence?): StringBuilder {
        AbstractList.checkPositionIndex(index, _length)
        return insert(index, value?.toString() ?: "null")
    }

    /**
     * Inserts the string representation of the specified object [value] into this string builder at the specified [index] and returns this instance.
     *
     * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
     * and then that string was inserted into this string builder at the specified [index].
     *
     * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
     */
    @SinceKotlin("1.4")
    @IgnorableReturnValue
    actual fun insert(index: Int, value: Any?): StringBuilder =
        insert(index, value.toString())

    /**
     * Inserts the string [value] into this string builder at the specified [index] and returns this instance.
     *
     * If [value] is `null`, then the four characters `"null"` are inserted.
     *
     * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
     */
    @SinceKotlin("1.4")
    @IgnorableReturnValue
    actual fun insert(index: Int, value: String?): StringBuilder {
        AbstractList.checkPositionIndex(index, _length)
        val toInsert = value ?: "null"
        if (toInsert.isEmpty()) return this
        if (index == _length) {
            appendChunk(toInsert)
            return this
        }
        val s = materialize()
        replaceWith(s.substring(0, index) + toInsert + s.substring(index))
        return this
    }

    /**
     *  Sets the length of this string builder to the specified [newLength].
     *
     *  If the [newLength] is less than the current length, it is changed to the specified [newLength].
     *  Otherwise, null characters '\u0000' are appended to this string builder until its length is less than the [newLength].
     *
     *  Note that in Kotlin/JS [set] operator function has non-constant execution time complexity.
     *  Therefore, increasing length of this string builder and then updating each character by index may slow down your program.
     *
     *  @throws IndexOutOfBoundsException or [IllegalArgumentException] if [newLength] is less than zero.
     */
    @SinceKotlin("1.4")
    actual fun setLength(newLength: Int) {
        if (newLength < 0) {
            throw IllegalArgumentException("Negative new length: $newLength.")
        }
        if (newLength < _length) {
            replaceWith(materialize().substring(0, newLength))
        } else if (newLength > _length) {
            val padCount = newLength - _length
            val padBuf: dynamic = luaEmptyTable()
            var i = 1
            while (i <= padCount) {
                luaTableSet(padBuf, i, "\u0000")
                i++
            }
            appendChunk(LuaTable.concat(padBuf))
        }
    }

    /**
     * Returns a new [String] that contains characters in this string builder at [startIndex] (inclusive) and up to the [length] (exclusive).
     *
     * @throws IndexOutOfBoundsException if [startIndex] is less than zero or greater than the length of this string builder.
     */
    @SinceKotlin("1.4")
    actual fun substring(startIndex: Int): String {
        AbstractList.checkPositionIndex(startIndex, _length)
        return materialize().substring(startIndex)
    }

    /**
     * Returns a new [String] that contains characters in this string builder at [startIndex] (inclusive) and up to the [endIndex] (exclusive).
     *
     * @throws IndexOutOfBoundsException or [IllegalArgumentException] when [startIndex] or [endIndex] is out of range of this string builder indices or when `startIndex > endIndex`.
     */
    @SinceKotlin("1.4")
    actual fun substring(startIndex: Int, endIndex: Int): String {
        AbstractList.checkBoundsIndexes(startIndex, endIndex, _length)
        return materialize().substring(startIndex, endIndex)
    }

    /**
     * Attempts to reduce storage used for this string builder.
     *
     * If the backing storage of this string builder is larger than necessary to hold its current contents,
     * then it may be resized to become more space efficient.
     * Calling this method may, but is not required to, affect the value of the [capacity] property.
     */
    @SinceKotlin("1.4")
    actual fun trimToSize() {
        if (partCount > 1) {
            materialize()
        }
    }

    override fun toString(): String = materialize()

    internal fun clearInternal() {
        parts = luaEmptyTable()
        partCount = 0
        _length = 0
    }

    internal fun setCharAtInternal(index: Int, value: Char) {
        AbstractList.checkElementIndex(index, _length)
        val s = materialize()
        replaceWith(s.substring(0, index) + value + s.substring(index + 1))
    }

    internal fun setRangeInternal(startIndex: Int, endIndex: Int, value: String) {
        checkReplaceRange(startIndex, endIndex, _length)
        val actualEnd = if (endIndex > _length) _length else endIndex
        val s = materialize()
        replaceWith(s.substring(0, startIndex) + value + s.substring(actualEnd))
    }

    internal fun deleteAtInternal(index: Int) {
        AbstractList.checkElementIndex(index, _length)
        val s = materialize()
        replaceWith(s.substring(0, index) + s.substring(index + 1))
    }

    internal fun deleteRangeInternal(startIndex: Int, endIndex: Int) {
        checkReplaceRange(startIndex, endIndex, _length)
        val actualEnd = if (endIndex > _length) _length else endIndex
        if (startIndex < actualEnd) {
            val s = materialize()
            replaceWith(s.substring(0, startIndex) + s.substring(actualEnd))
        }
    }

    internal fun toCharArrayInternal(
        destination: CharArray,
        destinationOffset: Int,
        startIndex: Int,
        endIndex: Int,
    ) {
        AbstractList.checkBoundsIndexes(startIndex, endIndex, _length)
        AbstractList.checkBoundsIndexes(destinationOffset, destinationOffset + endIndex - startIndex, destination.size)
        if (startIndex == endIndex) return
        val s = materialize()
        var dstIndex = destinationOffset
        var i = startIndex
        while (i < endIndex) {
            destination[dstIndex++] = s[i++]
        }
    }

    private fun checkReplaceRange(startIndex: Int, endIndex: Int, length: Int) {
        if (startIndex < 0 || startIndex > length) {
            throw IndexOutOfBoundsException("startIndex: $startIndex, length: $length")
        }
        if (startIndex > endIndex) {
            throw IllegalArgumentException("startIndex($startIndex) > endIndex($endIndex)")
        }
    }
}

private fun charArraySliceToString(
    value: CharArray,
    startIndex: Int,
    endIndex: Int,
): String {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, value.size)
    if (startIndex == endIndex) return ""
    val buf: dynamic = luaEmptyTable()
    var k = 1
    var i = startIndex
    while (i < endIndex) {
        luaTableSet(buf, k, value[i].toString())
        k++
        i++
    }
    return LuaTable.concat(buf)
}

private fun charSequenceSliceToString(
    value: CharSequence,
    startIndex: Int,
    endIndex: Int,
): String {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, value.length)
    if (startIndex == endIndex) return ""
    if (value is String) return value.substring(startIndex, endIndex)
    if (value is StringBuilder) return value.substring(startIndex, endIndex)
    val buf: dynamic = luaEmptyTable()
    var k = 1
    var i = startIndex
    while (i < endIndex) {
        luaTableSet(buf, k, value[i].toString())
        k++
        i++
    }
    return LuaTable.concat(buf)
}

/**
 * Appends the string representation of the specified byte [value] to this string builder and returns this instance.
 *
 * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
 * and then that string was appended to this string builder.
 */
@SinceKotlin("1.9")
@IgnorableReturnValue
actual fun StringBuilder.append(value: Byte): StringBuilder = append(value.toInt())

/**
 * Appends the string representation of the specified short [value] to this string builder and returns this instance.
 *
 * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
 * and then that string was appended to this string builder.
 */
@SinceKotlin("1.9")
@IgnorableReturnValue
actual fun StringBuilder.append(value: Short): StringBuilder = append(value.toInt())

/**
 * Inserts the string representation of the specified byte [value] into this string builder at the specified [index] and returns this instance.
 *
 * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
 * and then that string was inserted into this string builder at the specified [index].
 *
 * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
 */
@SinceKotlin("1.9")
@IgnorableReturnValue
actual fun StringBuilder.insert(index: Int, value: Byte): StringBuilder = insert(index, value.toInt())

/**
 * Inserts the string representation of the specified short [value] into this string builder at the specified [index] and returns this instance.
 *
 * The overall effect is exactly as if the [value] were converted to a string by the `value.toString()` method,
 * and then that string was inserted into this string builder at the specified [index].
 *
 * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
 */
@SinceKotlin("1.9")
@IgnorableReturnValue
actual fun StringBuilder.insert(index: Int, value: Short): StringBuilder = insert(index, value.toInt())

/**
 * Clears the content of this string builder making it empty and returns this instance.
 *
 * @sample samples.text.Strings.clearStringBuilder
 */
@SinceKotlin("1.3")
@IgnorableReturnValue
actual fun StringBuilder.clear(): StringBuilder {
    clearInternal()
    return this
}

/**
 * Sets the character at the specified [index] to the specified [value].
 *
 * @throws IndexOutOfBoundsException if [index] is out of bounds of this string builder.
 */
@SinceKotlin("1.4")
actual operator fun StringBuilder.set(index: Int, value: Char) {
    setCharAtInternal(index, value)
}

/**
 * Replaces characters in the specified range of this string builder with characters in the specified string [value] and returns this instance.
 *
 * @param startIndex the beginning (inclusive) of the range to replace.
 * @param endIndex the end (exclusive) of the range to replace.
 * @param value the string to replace with.
 *
 * @throws IndexOutOfBoundsException or [IllegalArgumentException] if [startIndex] is less than zero, greater than the length of this string builder, or `startIndex > endIndex`.
 */
@SinceKotlin("1.4")
@IgnorableReturnValue
actual fun StringBuilder.setRange(startIndex: Int, endIndex: Int, value: String): StringBuilder {
    setRangeInternal(startIndex, endIndex, value)
    return this
}

/**
 * Removes the character at the specified [index] from this string builder and returns this instance.
 *
 * If the `Char` at the specified [index] is part of a supplementary code point, this method does not remove the entire supplementary character.
 *
 * @param index the index of `Char` to remove.
 *
 * @throws IndexOutOfBoundsException if [index] is out of bounds of this string builder.
 */
@SinceKotlin("1.4")
@IgnorableReturnValue
actual fun StringBuilder.deleteAt(index: Int): StringBuilder {
    deleteAtInternal(index)
    return this
}

/**
 * Removes characters in the specified range from this string builder and returns this instance.
 *
 * @param startIndex the beginning (inclusive) of the range to remove.
 * @param endIndex the end (exclusive) of the range to remove.
 *
 * @throws IndexOutOfBoundsException or [IllegalArgumentException] when [startIndex] is out of range of this string builder indices or when `startIndex > endIndex`.
 */
@SinceKotlin("1.4")
@IgnorableReturnValue
actual fun StringBuilder.deleteRange(startIndex: Int, endIndex: Int): StringBuilder {
    deleteRangeInternal(startIndex, endIndex)
    return this
}

/**
 * Copies characters from this string builder into the [destination] character array.
 *
 * @param destination the array to copy to.
 * @param destinationOffset the position in the array to copy to, 0 by default.
 * @param startIndex the beginning (inclusive) of the range to copy, 0 by default.
 * @param endIndex the end (exclusive) of the range to copy, length of this string builder by default.
 *
 * @throws IndexOutOfBoundsException or [IllegalArgumentException] when [startIndex] or [endIndex] is out of range of this string builder indices or when `startIndex > endIndex`.
 * @throws IndexOutOfBoundsException when the subrange doesn't fit into the [destination] array starting at the specified [destinationOffset],
 *  or when that index is out of the [destination] array indices range.
 */
@SinceKotlin("1.4")
actual fun StringBuilder.toCharArray(destination: CharArray, destinationOffset: Int, startIndex: Int, endIndex: Int) {
    toCharArrayInternal(destination, destinationOffset, startIndex, endIndex)
}

/**
 * Appends characters in a subarray of the specified character array [value] to this string builder and returns this instance.
 *
 * Characters are appended in order, starting at specified [startIndex].
 *
 * @param value the array from which characters are appended.
 * @param startIndex the beginning (inclusive) of the subarray to append.
 * @param endIndex the end (exclusive) of the subarray to append.
 *
 * @throws IndexOutOfBoundsException or [IllegalArgumentException] when [startIndex] or [endIndex] is out of range of the [value] array indices or when `startIndex > endIndex`.
 */
@SinceKotlin("1.4")
@IgnorableReturnValue
actual fun StringBuilder.appendRange(value: CharArray, startIndex: Int, endIndex: Int): StringBuilder {
    appendChunk(charArraySliceToString(value, startIndex, endIndex))
    return this
}

/**
 * Appends a subsequence of the specified character sequence [value] to this string builder and returns this instance.
 *
 * @param value the character sequence from which a subsequence is appended.
 * @param startIndex the beginning (inclusive) of the subsequence to append.
 * @param endIndex the end (exclusive) of the subsequence to append.
 *
 * @throws IndexOutOfBoundsException or [IllegalArgumentException] when [startIndex] or [endIndex] is out of range of the [value] character sequence indices or when `startIndex > endIndex`.
 */
@SinceKotlin("1.4")
@IgnorableReturnValue
actual fun StringBuilder.appendRange(value: CharSequence, startIndex: Int, endIndex: Int): StringBuilder {
    appendChunk(charSequenceSliceToString(value, startIndex, endIndex))
    return this
}

/**
 * Inserts characters in a subarray of the specified character array [value] into this string builder at the specified [index] and returns this instance.
 *
 * The inserted characters go in same order as in the [value] array, starting at [index].
 *
 * @param index the position in this string builder to insert at.
 * @param value the array from which characters are inserted.
 * @param startIndex the beginning (inclusive) of the subarray to insert.
 * @param endIndex the end (exclusive) of the subarray to insert.
 *
 * @throws IndexOutOfBoundsException or [IllegalArgumentException] when [startIndex] or [endIndex] is out of range of the [value] array indices or when `startIndex > endIndex`.
 * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
 */
@SinceKotlin("1.4")
@IgnorableReturnValue
actual fun StringBuilder.insertRange(index: Int, value: CharArray, startIndex: Int, endIndex: Int): StringBuilder {
    AbstractList.checkPositionIndex(index, length)
    return insert(index, charArraySliceToString(value, startIndex, endIndex))
}

/**
 * Inserts characters in a subsequence of the specified character sequence [value] into this string builder at the specified [index] and returns this instance.
 *
 * The inserted characters go in the same order as in the [value] character sequence, starting at [index].
 *
 * @param index the position in this string builder to insert at.
 * @param value the character sequence from which a subsequence is inserted.
 * @param startIndex the beginning (inclusive) of the subsequence to insert.
 * @param endIndex the end (exclusive) of the subsequence to insert.
 *
 * @throws IndexOutOfBoundsException or [IllegalArgumentException] when [startIndex] or [endIndex] is out of range of the [value] character sequence indices or when `startIndex > endIndex`.
 * @throws IndexOutOfBoundsException if [index] is less than zero or greater than the length of this string builder.
 */
@SinceKotlin("1.4")
@IgnorableReturnValue
actual fun StringBuilder.insertRange(index: Int, value: CharSequence, startIndex: Int, endIndex: Int): StringBuilder {
    AbstractList.checkPositionIndex(index, length)
    return insert(index, charSequenceSliceToString(value, startIndex, endIndex))
}

/** Appends [value] to this [StringBuilder], followed by a line feed character (`\n`). */
@SinceKotlin("1.9")
@IgnorableReturnValue
actual fun StringBuilder.appendLine(value: Byte): StringBuilder = append(value.toInt()).append('\n')

/** Appends [value] to this [StringBuilder], followed by a line feed character (`\n`). */
@SinceKotlin("1.9")
@IgnorableReturnValue
actual fun StringBuilder.appendLine(value: Short): StringBuilder = append(value.toInt()).append('\n')

/** Appends [value] to this [StringBuilder], followed by a line feed character (`\n`). */
@SinceKotlin("1.9")
@IgnorableReturnValue
actual fun StringBuilder.appendLine(value: Int): StringBuilder = append(value).append('\n')

/** Appends [value] to this [StringBuilder], followed by a line feed character (`\n`). */
@SinceKotlin("1.9")
@IgnorableReturnValue
actual fun StringBuilder.appendLine(value: Long): StringBuilder = append(value).append('\n')

/** Appends [value] to this [StringBuilder], followed by a line feed character (`\n`). */
@SinceKotlin("1.9")
@IgnorableReturnValue
actual fun StringBuilder.appendLine(value: Float): StringBuilder = append(value).append('\n')

/** Appends [value] to this [StringBuilder], followed by a line feed character (`\n`). */
@SinceKotlin("1.9")
@IgnorableReturnValue
actual fun StringBuilder.appendLine(value: Double): StringBuilder = append(value).append('\n')
