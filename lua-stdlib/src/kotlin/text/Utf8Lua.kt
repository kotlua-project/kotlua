@file:OptIn(LuaIntrinsic::class)
package kotlin.text

import kotlin.lua.LuaIntrinsic
import kotlin.lua.luaEmptyTable
import kotlin.lua.luaTableSet

private const val REPLACEMENT_STR = "\uFFFD"

/**
 * Decodes a string from the bytes in UTF-8 encoding in this array.
 *
 * Malformed byte sequences are replaced by the replacement char `\uFFFD`.
 */
@SinceKotlin("1.4")
public actual fun ByteArray.decodeToString(): String =
    decodeToString(0, size, false)

/**
 * Decodes a string from the bytes in UTF-8 encoding in this array or its subrange.
 *
 * @param startIndex the beginning (inclusive) of the subrange to decode, 0 by default.
 * @param endIndex the end (exclusive) of the subrange to decode, size of this array by default.
 * @param throwOnInvalidSequence specifies whether to throw an exception on malformed byte sequence or replace it by the replacement char `\uFFFD`.
 *
 * @throws IndexOutOfBoundsException if [startIndex] is less than zero or [endIndex] is greater than the size of this array.
 * @throws IllegalArgumentException if [startIndex] is greater than [endIndex].
 * @throws CharacterCodingException if the byte array contains malformed UTF-8 byte sequence and [throwOnInvalidSequence] is true.
 */
@SinceKotlin("1.4")
public actual fun ByteArray.decodeToString(
    startIndex: Int,
    endIndex: Int,
    throwOnInvalidSequence: Boolean,
): String {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, size)
    if (startIndex == endIndex) return ""

    val out: dynamic = luaEmptyTable()
    var k = 1
    var i = startIndex

    fun failOrReplace() {
        if (throwOnInvalidSequence) throw CharacterCodingException()
        luaTableSet(out, k++, REPLACEMENT_STR)
    }

    while (i < endIndex) {
        val b0 = this[i].toInt() and 0xFF
        when {
            b0 < 0x80 -> {
                luaTableSet(out, k++, LuaString.char(b0))
                i++
            }
            b0 in 0xC2..0xDF -> {
                if (i + 1 >= endIndex) {
                    failOrReplace()
                    i++
                } else {
                    val b1 = this[i + 1].toInt() and 0xFF
                    if (b1 and 0xC0 != 0x80) {
                        failOrReplace()
                        i++
                    } else {
                        luaTableSet(out, k++, LuaString.char(b0) + LuaString.char(b1))
                        i += 2
                    }
                }
            }
            b0 in 0xE0..0xEF -> {
                if (i + 2 >= endIndex) {
                    failOrReplace()
                    i++
                } else {
                    val b1 = this[i + 1].toInt() and 0xFF
                    val b2 = this[i + 2].toInt() and 0xFF
                    val validB1 = when (b0) {
                        0xE0 -> b1 in 0xA0..0xBF
                        0xED -> b1 in 0x80..0x9F
                        else -> b1 in 0x80..0xBF
                    }
                    if (!validB1 || (b2 and 0xC0) != 0x80) {
                        failOrReplace()
                        i++
                    } else {
                        luaTableSet(out, k++, LuaString.char(b0) + LuaString.char(b1) + LuaString.char(b2))
                        i += 3
                    }
                }
            }
            b0 in 0xF0..0xF4 -> {
                if (i + 3 >= endIndex) {
                    failOrReplace()
                    i++
                } else {
                    val b1 = this[i + 1].toInt() and 0xFF
                    val b2 = this[i + 2].toInt() and 0xFF
                    val b3 = this[i + 3].toInt() and 0xFF
                    val validB1 = when (b0) {
                        0xF0 -> b1 in 0x90..0xBF
                        0xF4 -> b1 in 0x80..0x8F
                        else -> b1 in 0x80..0xBF
                    }
                    if (!validB1 || (b2 and 0xC0) != 0x80 || (b3 and 0xC0) != 0x80) {
                        failOrReplace()
                        i++
                    } else {
                        luaTableSet(
                            out,
                            k++,
                            LuaString.char(b0) + LuaString.char(b1) + LuaString.char(b2) + LuaString.char(b3),
                        )
                        i += 4
                    }
                }
            }
            else -> {
                failOrReplace()
                i++
            }
        }
    }
    return LuaTable.concat(out)
}

/**
 * Encodes this string to an array of bytes in UTF-8 encoding.
 *
 * Any malformed char sequence is replaced by the replacement byte sequence.
 *
 * @sample samples.text.Strings.encodeToByteArray
 */
@SinceKotlin("1.4")
public actual fun String.encodeToByteArray(): ByteArray =
    encodeToByteArray(0, length, false)

/**
 * Encodes this string or its substring to an array of bytes in UTF-8 encoding.
 *
 * @param startIndex the beginning (inclusive) of the substring to encode, 0 by default.
 * @param endIndex the end (exclusive) of the substring to encode, length of this string by default.
 * @param throwOnInvalidSequence specifies whether to throw an exception on malformed char sequence or replace.
 *
 * @throws IndexOutOfBoundsException if [startIndex] is less than zero or [endIndex] is greater than the length of this string.
 * @throws IllegalArgumentException if [startIndex] is greater than [endIndex].
 * @throws CharacterCodingException if this string contains malformed char sequence and [throwOnInvalidSequence] is true.
 *
 * @sample samples.text.Strings.encodeToByteArray
 */
@SinceKotlin("1.4")
public actual fun String.encodeToByteArray(
    startIndex: Int,
    endIndex: Int,
    throwOnInvalidSequence: Boolean,
): ByteArray {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, length)
    val len = endIndex - startIndex
    if (len == 0) return ByteArray(0)

    val buf = ByteArray(len * 3)
    var outLen = 0
    var i = startIndex

    fun failOrReplace() {
        if (throwOnInvalidSequence) throw CharacterCodingException()
        buf[outLen++] = 0xEF.toByte()
        buf[outLen++] = 0xBF.toByte()
        buf[outLen++] = 0xBD.toByte()
    }

    while (i < endIndex) {
        val b0 = LuaString.byte(this, i + 1)
        when {
            b0 < 0x80 -> {
                buf[outLen++] = b0.toByte()
                i++
            }
            b0 in 0xC2..0xDF && i + 1 < endIndex -> {
                val b1 = LuaString.byte(this, i + 2)
                if ((b1 and 0xC0) != 0x80) {
                    failOrReplace()
                    i++
                } else {
                    buf[outLen++] = b0.toByte()
                    buf[outLen++] = b1.toByte()
                    i += 2
                }
            }
            b0 in 0xE0..0xEF && i + 2 < endIndex -> {
                val b1 = LuaString.byte(this, i + 2)
                val b2 = LuaString.byte(this, i + 3)
                val validB1 = when (b0) {
                    0xE0 -> b1 in 0xA0..0xBF
                    0xED -> b1 in 0x80..0x9F
                    else -> b1 in 0x80..0xBF
                }
                if (!validB1 || (b2 and 0xC0) != 0x80) {
                    failOrReplace()
                    i++
                } else {
                    buf[outLen++] = b0.toByte()
                    buf[outLen++] = b1.toByte()
                    buf[outLen++] = b2.toByte()
                    i += 3
                }
            }
            b0 in 0xF0..0xF4 && i + 3 < endIndex -> {
                val b1 = LuaString.byte(this, i + 2)
                val b2 = LuaString.byte(this, i + 3)
                val b3 = LuaString.byte(this, i + 4)
                val validB1 = when (b0) {
                    0xF0 -> b1 in 0x90..0xBF
                    0xF4 -> b1 in 0x80..0x8F
                    else -> b1 in 0x80..0xBF
                }
                if (!validB1 || (b2 and 0xC0) != 0x80 || (b3 and 0xC0) != 0x80) {
                    failOrReplace()
                    i++
                } else {
                    buf[outLen++] = b0.toByte()
                    buf[outLen++] = b1.toByte()
                    buf[outLen++] = b2.toByte()
                    buf[outLen++] = b3.toByte()
                    i += 4
                }
            }
            else -> {
                failOrReplace()
                i++
            }
        }
    }

    return if (outLen == buf.size) buf else buf.copyOf(outLen)
}