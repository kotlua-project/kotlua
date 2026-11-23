@file:OptIn(LuaIntrinsic::class, ExperimentalUuidApi::class)

package kotlin.uuid

import kotlin.lua.LuaIntrinsic
import kotlin.lua.LuaMath
import kotlin.lua.luaDiv
import kotlin.lua.luaMod
import kotlin.lua.luaMult
import kotlin.lua.luaPlus
import kotlin.random.Random

internal actual fun serializedUuid(uuid: Uuid): Any = uuid

internal actual fun secureRandomBytes(destination: ByteArray) {
    val _ = Random.nextBytes(destination)
}

private fun toInt32(u: Any?): Int {
    val d = u.unsafeCast<Double>()
    return if (d >= 2147483648.0) (d - 4294967296.0).toInt() else d.toInt()
}

private fun longLow32(v: Long): Int =
    toInt32(luaMod(v, 4294967296.0))

private fun longHigh32(v: Long): Int {
    val q = LuaMath.floor(luaDiv(v, 4294967296.0))
    return toInt32(luaMod(q, 4294967296.0))
}

private fun longFromParts(hi: Int, lo: Int): Long {
    val uLo: Double = if (lo < 0) lo.toDouble() + 4294967296.0 else lo.toDouble()
    return luaPlus(luaMult(hi.toDouble(), 4294967296.0), uLo).unsafeCast<Long>()
}

internal actual fun ByteArray.getLongAt(index: Int): Long {
    val hi =
        ((this[index].toInt() and 0xFF) shl 24) or
                ((this[index + 1].toInt() and 0xFF) shl 16) or
                ((this[index + 2].toInt() and 0xFF) shl 8) or
                (this[index + 3].toInt() and 0xFF)
    val lo =
        ((this[index + 4].toInt() and 0xFF) shl 24) or
                ((this[index + 5].toInt() and 0xFF) shl 16) or
                ((this[index + 6].toInt() and 0xFF) shl 8) or
                (this[index + 7].toInt() and 0xFF)
    return longFromParts(hi, lo)
}

internal actual fun Long.formatBytesInto(
    dst: ByteArray,
    dstOffset: Int,
    startIndex: Int,
    endIndex: Int,
) {
    val hi = longHigh32(this)
    val lo = longLow32(this)
    var dstIndex = dstOffset
    for (byteIndex in startIndex until endIndex) {
        val word = if (byteIndex < 4) hi else lo
        val shift = (3 - (byteIndex and 3)) shl 3
        val b = (word ushr shift) and 0xFF
        val hiNibble = (b ushr 4) and 0xF
        val loNibble = b and 0xF
        dst[dstIndex++] = (if (hiNibble < 10) ('0'.code + hiNibble) else ('a'.code + hiNibble - 10)).toByte()
        dst[dstIndex++] = (if (loNibble < 10) ('0'.code + loNibble) else ('a'.code + loNibble - 10)).toByte()
    }
}

internal actual fun ByteArray.setLongAt(index: Int, value: Long) {
    val hi = longHigh32(value)
    val lo = longLow32(value)
    this[index] = (hi ushr 24).toByte()
    this[index + 1] = (hi ushr 16).toByte()
    this[index + 2] = (hi ushr 8).toByte()
    this[index + 3] = hi.toByte()
    this[index + 4] = (lo ushr 24).toByte()
    this[index + 5] = (lo ushr 16).toByte()
    this[index + 6] = (lo ushr 8).toByte()
    this[index + 7] = lo.toByte()
}

private fun hexDigitAt(s: String, index: Int): Int =
    when (val c = s[index]) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

private inline fun parseHexWord(
    s: String,
    startIndex: Int,
    endIndex: Int,
    onError: (Int) -> Nothing,
): Int {
    var result = 0
    for (i in startIndex until endIndex) {
        val d = hexDigitAt(s, i)
        if (d < 0) onError(i)
        result = (result shl 4) or d
    }
    return result
}

private inline fun parseHexDashImpl(
    hexDashString: String,
    onError: (String, String, Int) -> Nothing,
): Uuid {
    val msg = "a hexadecimal digit"
    val msbHi = parseHexWord(hexDashString, 0, 8) { onError(hexDashString, msg, it) }
    hexDashString.uuidCheckHyphenAt(8, onError)
    val p2 = parseHexWord(hexDashString, 9, 13) { onError(hexDashString, msg, it) }
    hexDashString.uuidCheckHyphenAt(13, onError)
    val p3 = parseHexWord(hexDashString, 14, 18) { onError(hexDashString, msg, it) }
    hexDashString.uuidCheckHyphenAt(18, onError)
    val p4 = parseHexWord(hexDashString, 19, 23) { onError(hexDashString, msg, it) }
    hexDashString.uuidCheckHyphenAt(23, onError)
    val p5Hi = parseHexWord(hexDashString, 24, 28) { onError(hexDashString, msg, it) }
    val p5Lo = parseHexWord(hexDashString, 28, 36) { onError(hexDashString, msg, it) }

    val msb = longFromParts(msbHi, (p2 shl 16) or p3)
    val lsb = longFromParts((p4 shl 16) or p5Hi, p5Lo)
    return Uuid.fromLongs(msb, lsb)
}

internal actual fun uuidParseHexDash(hexDashString: String): Uuid =
    parseHexDashImpl(hexDashString) { input, desc, pos ->
        uuidThrowUnexpectedCharacterException(input, desc, pos)
    }

internal actual fun uuidParseHexDashOrNull(hexDashString: String): Uuid? =
    parseHexDashImpl(hexDashString) { _, _, _ ->
        return null
    }

private inline fun parseHexImpl(
    hexString: String,
    onError: (String, String, Int) -> Nothing,
): Uuid {
    val msg = "a hexadecimal digit"
    val msbHi = parseHexWord(hexString, 0, 8) { onError(hexString, msg, it) }
    val msbLo = parseHexWord(hexString, 8, 16) { onError(hexString, msg, it) }
    val lsbHi = parseHexWord(hexString, 16, 24) { onError(hexString, msg, it) }
    val lsbLo = parseHexWord(hexString, 24, 32) { onError(hexString, msg, it) }
    return Uuid.fromLongs(longFromParts(msbHi, msbLo), longFromParts(lsbHi, lsbLo))
}

internal actual fun uuidParseHex(hexString: String): Uuid =
    parseHexImpl(hexString) { input, desc, pos ->
        uuidThrowUnexpectedCharacterException(input, desc, pos)
    }

internal actual fun uuidParseHexOrNull(hexString: String): Uuid? =
    parseHexImpl(hexString) { _, _, _ ->
        return null
    }
