package kotlin.text

@PublishedApi
@IgnorableReturnValue
internal actual fun checkRadix(radix: Int): Int {
    if (radix !in 2..36) {
        throw IllegalArgumentException("radix $radix was not in valid range ${2..36}")
    }
    return radix
}

internal actual fun digitOf(char: Char, radix: Int): Int {
    val value = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'z' -> char - 'a' + 10
        in 'A'..'Z' -> char - 'A' + 10
        else -> -1
    }
    return if (value in 0 until radix) value else -1
}

private fun parseSignedInt(input: String, radix: Int, minValue: Int, maxValue: Int): Int {
    checkRadix(radix)
    val len = input.length
    if (len == 0) throw NumberFormatException("For input string: \"$input\"")

    var start = 0
    var isNegative = false
    var limit = -maxValue
    val firstChar = input[0]
    if (firstChar < '0') {
        if (firstChar == '-') {
            isNegative = true
            limit = minValue
        } else if (firstChar != '+') {
            throw NumberFormatException("For input string: \"$input\"")
        }
        if (len == 1) throw NumberFormatException("For input string: \"$input\"")
        start = 1
    }

    val limitBeforeMul = limit / radix
    var result = 0
    for (i in start until len) {
        val digit = digitOf(input[i], radix)
        if (digit < 0 || result < limitBeforeMul) {
            throw NumberFormatException("For input string: \"$input\"")
        }
        result *= radix
        if (result < limit + digit) {
            throw NumberFormatException("For input string: \"$input\"")
        }
        result -= digit
    }
    return if (isNegative) result else -result
}

private fun parseSignedLong(input: String, radix: Int): Long {
    checkRadix(radix)
    val len = input.length
    if (len == 0) throw NumberFormatException("For input string: \"$input\"")

    var start = 0
    var isNegative = false
    var limit = -Long.MAX_VALUE
    val firstChar = input[0]
    if (firstChar < '0') {
        if (firstChar == '-') {
            isNegative = true
            limit = Long.MIN_VALUE
        } else if (firstChar != '+') {
            throw NumberFormatException("For input string: \"$input\"")
        }
        if (len == 1) throw NumberFormatException("For input string: \"$input\"")
        start = 1
    }

    val limitBeforeMul = limit / radix
    var result = 0L
    for (i in start until len) {
        val digit = digitOf(input[i], radix)
        if (digit < 0 || result < limitBeforeMul) {
            throw NumberFormatException("For input string: \"$input\"")
        }
        result *= radix
        if (result < limit + digit) {
            throw NumberFormatException("For input string: \"$input\"")
        }
        result -= digit
    }
    return if (isNegative) result else -result
}

/**
 * Parses the string to a [Byte] number.
 *
 * The string must consist of an optional leading `+` or `-` sign and decimal digits (`0-9`),
 * and fit the valid [Byte] value range (within `Byte.MIN_VALUE..Byte.MAX_VALUE`),
 * otherwise a [NumberFormatException] will be thrown.
 *
 * @throws NumberFormatException if the string is not a valid representation of a [Byte].
 * @sample samples.text.Numbers.toByte
 */
public actual fun String.toByte(): Byte = toByte(10)

/**
 * Parses the string as a signed [Byte] number and returns the result.
 * @throws NumberFormatException if the string is not a valid representation of a number.
 * @throws IllegalArgumentException when [radix] is not a valid radix for string to number conversion.
 */
public actual fun String.toByte(radix: Int): Byte =
    parseSignedInt(this, radix, Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toByte()

/**
 * Parses the string to a [Short] number.
 *
 * The string must consist of an optional leading `+` or `-` sign and decimal digits (`0-9`),
 * and fit the valid [Short] value range (within `Short.MIN_VALUE..Short.MAX_VALUE`),
 * otherwise a [NumberFormatException] will be thrown.
 *
 * @throws NumberFormatException if the string is not a valid representation of a [Short].
 * @sample samples.text.Numbers.toShort
 */
public actual fun String.toShort(): Short = toShort(10)

/**
 * Parses the string as a [Short] number and returns the result.
 * @throws NumberFormatException if the string is not a valid representation of a number.
 * @throws IllegalArgumentException when [radix] is not a valid radix for string to number conversion.
 */
public actual fun String.toShort(radix: Int): Short =
    parseSignedInt(this, radix, Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

/**
 * Parses the string to an [Int] number.
 *
 * The string must consist of an optional leading `+` or `-` sign and decimal digits (`0-9`),
 * and fit the valid [Int] value range (within `Int.MIN_VALUE..Int.MAX_VALUE`),
 * otherwise a [NumberFormatException] will be thrown.
 *
 * @throws NumberFormatException if the string is not a valid representation of an [Int].
 * @sample samples.text.Numbers.toInt
 */
public actual fun String.toInt(): Int = toInt(10)

/**
 * Parses the string as an [Int] number and returns the result.
 * @throws NumberFormatException if the string is not a valid representation of a number.
 * @throws IllegalArgumentException when [radix] is not a valid radix for string to number conversion.
 */
public actual fun String.toInt(radix: Int): Int =
    parseSignedInt(this, radix, Int.MIN_VALUE, Int.MAX_VALUE)

/**
 * Parses the string to a [Long] number.
 *
 * The string must consist of an optional leading `+` or `-` sign and decimal digits (`0-9`),
 * and fit the valid [Long] value range (within `Long.MIN_VALUE..Long.MAX_VALUE`),
 * otherwise a [NumberFormatException] will be thrown.
 *
 * @throws NumberFormatException if the string is not a valid representation of a [Long].
 * @sample samples.text.Numbers.toLong
 */
public actual fun String.toLong(): Long = toLong(10)

/**
 * Parses the string as a [Long] number and returns the result.
 * @throws NumberFormatException if the string is not a valid representation of a number.
 * @throws IllegalArgumentException when [radix] is not a valid radix for string to number conversion.
 */
public actual fun String.toLong(radix: Int): Long =
    parseSignedLong(this, radix)

private fun parseDoubleOrNullImpl(input: String): Double? {
    val s = input.trim()
    if (s.isEmpty()) return null

    var idx = 0
    var sign = 1.0
    if (s[idx] == '+') {
        idx++
    } else if (s[idx] == '-') {
        sign = -1.0
        idx++
    }
    if (idx >= s.length) return null

    val rest = s.substring(idx)
    if (rest == "NaN") return Double.NaN
    if (rest == "Infinity") return if (sign > 0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY

    // Validate decimal floating-point syntax so Lua's tonumber doesn't accept hex like "0x10".
    var pos = 0
    val n = rest.length
    var hasDigits = false
    while (pos < n && rest[pos] in '0'..'9') {
        hasDigits = true
        pos++
    }
    if (pos < n && rest[pos] == '.') {
        pos++
        while (pos < n && rest[pos] in '0'..'9') {
            hasDigits = true
            pos++
        }
    }
    if (!hasDigits) return null
    if (pos < n && (rest[pos] == 'e' || rest[pos] == 'E')) {
        pos++
        if (pos < n && (rest[pos] == '+' || rest[pos] == '-')) {
            pos++
        }
        var hasExpDigits = false
        while (pos < n && rest[pos] in '0'..'9') {
            hasExpDigits = true
            pos++
        }
        if (!hasExpDigits) return null
    }
    if (pos != n) return null

    val parsed: Double? = lua("tonumber(s)")
    return parsed
}

/**
 * Parses the string as a [Double] number and returns the result.
 * @throws NumberFormatException if the string is not a valid representation of a number.
 */
public actual fun String.toDouble(): Double =
    toDoubleOrNull() ?: throw NumberFormatException("For input string: \"$this\"")

/**
 * Parses the string as a [Float] number and returns the result.
 * @throws NumberFormatException if the string is not a valid representation of a number.
 */
public actual fun String.toFloat(): Float =
    toFloatOrNull() ?: throw NumberFormatException("For input string: \"$this\"")

/**
 * Parses the string as a [Double] number and returns the result
 * or `null` if the string is not a valid representation of a number.
 */
public actual fun String.toDoubleOrNull(): Double? =
    parseDoubleOrNullImpl(this)

/**
 * Parses the string as a [Float] number and returns the result
 * or `null` if the string is not a valid representation of a number.
 */
public actual fun String.toFloatOrNull(): Float? =
    parseDoubleOrNullImpl(this)?.toFloat()

/**
 * Returns a string representation of this [Byte] value in the specified [radix].
 *
 * @throws IllegalArgumentException when [radix] is not a valid radix for number to string conversion.
 */
@SinceKotlin("1.2")
public actual fun Byte.toString(radix: Int): String = toInt().toString(radix)

/**
 * Returns a string representation of this [Short] value in the specified [radix].
 *
 * @throws IllegalArgumentException when [radix] is not a valid radix for number to string conversion.
 */
@SinceKotlin("1.2")
public actual fun Short.toString(radix: Int): String = toInt().toString(radix)

/**
 * Returns a string representation of this [Int] value in the specified [radix].
 *
 * @throws IllegalArgumentException when [radix] is not a valid radix for number to string conversion.
 */
@SinceKotlin("1.2")
public actual fun Int.toString(radix: Int): String {
    checkRadix(radix)
    if (radix == 10) return toString()
    if (this == 0) return "0"
    val isNegative = this < 0
    var n = if (isNegative) this else -this
    val chars = CharArray(33)
    var pos = 33
    while (n < 0) {
        val q = n / radix
        val digit = -(n - q * radix)
        chars[--pos] = if (digit < 10) ('0' + digit) else ('a' + (digit - 10))
        n = q
    }
    if (isNegative) {
        chars[--pos] = '-'
    }
    return chars.concatToString(pos, 33)
}

/**
 * Returns a string representation of this [Long] value in the specified [radix].
 *
 * @throws IllegalArgumentException when [radix] is not a valid radix for number to string conversion.
 */
@SinceKotlin("1.2")
public actual fun Long.toString(radix: Int): String {
    checkRadix(radix)
    if (radix == 10) return toString()
    if (this == 0L) return "0"
    val isNegative = this < 0L
    var n = if (isNegative) this else -this
    val chars = CharArray(65)
    var pos = 65
    while (n < 0L) {
        val q = n / radix
        val digit = (-(n - q * radix)).toInt()
        chars[--pos] = if (digit < 10) ('0' + digit) else ('a' + (digit - 10))
        n = q
    }
    if (isNegative) {
        chars[--pos] = '-'
    }
    return chars.concatToString(pos, 65)
}
