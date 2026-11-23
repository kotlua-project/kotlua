@file:OptIn(LuaIntrinsic::class)

package kotlin

import kotlin.math.frexpExponent
import kotlin.math.frexpMantissa

/**
 * Returns `true` if the specified number is a
 * Not-a-Number (NaN) value, `false` otherwise.
 */
public actual fun Double.isNaN(): Boolean = luaNeq(this, this)

/**
 * Returns `true` if the specified number is a
 * Not-a-Number (NaN) value, `false` otherwise.
 */
public actual fun Float.isNaN(): Boolean = luaNeq(this, this)

/**
 * Returns `true` if this value is infinitely large in magnitude.
 */
public actual fun Double.isInfinite(): Boolean =
    this == Double.POSITIVE_INFINITY || this == Double.NEGATIVE_INFINITY

/**
 * Returns `true` if this value is infinitely large in magnitude.
 */
public actual fun Float.isInfinite(): Boolean =
    this == Float.POSITIVE_INFINITY || this == Float.NEGATIVE_INFINITY

/**
 * Returns `true` if the argument is a finite floating-point value; returns `false` otherwise (for `NaN` and infinity arguments).
 */
public actual fun Double.isFinite(): Boolean =
    this > Double.NEGATIVE_INFINITY && this < Double.POSITIVE_INFINITY

/**
 * Returns `true` if the argument is a finite floating-point value; returns `false` otherwise (for `NaN` and infinity arguments).
 */
public actual fun Float.isFinite(): Boolean =
    this > Float.NEGATIVE_INFINITY && this < Float.POSITIVE_INFINITY

/**
 * Returns a bit representation of the specified floating-point value as [Long]
 * according to the IEEE 754 floating-point "double format" bit layout.
 */
@SinceKotlin("1.2")
public actual fun Double.toBits(): Long {
    if (isNaN()) return 0x7ff8000000000000L
    return toRawBits()
}

private fun roundTiesToEven(x: Double): Double {
    val f: Double = lua("math.floor(x)")
    val diff = x - f
    if (diff < 0.5) return f
    if (diff > 0.5) return f + 1.0
    return if (luaMod(f, 2.0) == 0.0) f else f + 1.0
}

/**
 * Returns a bit representation of the specified floating-point value as [Long]
 * according to the IEEE 754 floating-point "double format" bit layout,
 * preserving `NaN` values exact layout.
 */
@SinceKotlin("1.2")
public actual fun Double.toRawBits(): Long {
    if (isNaN()) return 0x7ff8000000000000L
    val isNeg = this < 0.0 || (this == 0.0 && 1.0 / this < 0.0)
    val absVal = if (isNeg) -this else this
    val signHi = if (isNeg) Int.MIN_VALUE else 0

    if (absVal == 0.0) {
        return signHi.toLong() shl 32
    }
    if (absVal == Double.POSITIVE_INFINITY) {
        return (signHi or 0x7ff00000).toLong() shl 32
    }

    var biasedExp: Int
    var mantissa: Double
    if (absVal < 2.2250738585072014E-308) {
        biasedExp = 0
        mantissa = roundTiesToEven((absVal / 2.2250738585072014E-308) * 4503599627370496.0)
        if (mantissa >= 4503599627370496.0) {
            biasedExp = 1
            mantissa = 0.0
        }
    } else {
        val m = frexpMantissa(absVal)
        val exp = frexpExponent(absVal) - 1
        biasedExp = exp + 1023
        mantissa = roundTiesToEven((m * 2.0 - 1.0) * 4503599627370496.0)
        if (mantissa >= 4503599627370496.0) {
            mantissa = 0.0
            biasedExp++
        }
        if (biasedExp >= 2047) {
            return (signHi or 0x7ff00000).toLong() shl 32
        }
    }

    val mantHi = LuaMath.floor(mantissa / 4294967296.0).toInt()
    val mantLoD = mantissa - mantHi.toDouble() * 4294967296.0
    val mantLo = if (mantLoD >= 2147483648.0) (mantLoD - 4294967296.0).toInt() else mantLoD.toInt()
    val hi = signHi or (biasedExp shl 20) or mantHi
    return (hi.toLong() shl 32) or (mantLo.toLong() and 0xFFFFFFFFL)
}

/**
 * Returns the [Double] value corresponding to a given bit representation.
 */
@SinceKotlin("1.2")
public actual fun Double.Companion.fromBits(bits: Long): Double {
    val hi = (bits ushr 32).toInt()
    val lo = bits.toInt()
    val sign = if ((hi and Int.MIN_VALUE) != 0) -1.0 else 1.0
    val exp = (hi ushr 20) and 0x7FF
    val mantHi = hi and 0xFFFFF
    val mantLoD = if (lo < 0) lo.toDouble() + 4294967296.0 else lo.toDouble()
    val mantissa = mantHi.toDouble() * 4294967296.0 + mantLoD

    if (exp >= 2047) {
        return if (mantissa == 0.0) sign * Double.POSITIVE_INFINITY else Double.NaN
    }
    if (exp == 0) {
        if (mantissa == 0.0) return sign * 0.0
        return sign * (mantissa / 4503599627370496.0) * 2.2250738585072014E-308
    }
    return sign * (1.0 + mantissa / 4503599627370496.0) * luaPow(2.0, exp - 1023).unsafeCast<Double>()
}

@SinceKotlin("1.2")
public actual fun Float.toBits(): Int {
    if (isNaN()) return 0x7fc00000
    return toRawBits()
}

/**
 * Returns a bit representation of the specified floating-point value as [Int]
 * according to the IEEE 754 floating-point "single format" bit layout,
 * preserving `NaN` values exact layout.
 */
@SinceKotlin("1.2")
public actual fun Float.toRawBits(): Int {
    if (isNaN()) return 0x7fc00000
    val isNeg = this < 0.0f || (this == 0.0f && 1.0f / this < 0.0f)
    val signBit = if (isNeg) Int.MIN_VALUE else 0
    val absVal = if (isNeg) -this.toDouble() else this.toDouble()

    if (absVal == 0.0) return signBit
    if (absVal == Double.POSITIVE_INFINITY) return signBit or 0x7f800000

    val m = frexpMantissa(absVal)
    val e = frexpExponent(absVal)
    val exp = e - 1

    var biasedExp: Int
    var mantissa: Int
    if (exp >= -126) {
        biasedExp = exp + 127
        mantissa = roundTiesToEven((m * 2.0 - 1.0) * 8388608.0).toInt()
        if (mantissa >= 8388608) {
            mantissa = 0
            biasedExp++
        }
        if (biasedExp >= 255) {
            return signBit or 0x7f800000
        }
    } else {
        biasedExp = 0
        mantissa = roundTiesToEven(absVal * luaPow(2.0, 149).unsafeCast<Double>()).toInt()
        if (mantissa >= 8388608) {
            biasedExp = 1
            mantissa = 0
        }
    }

    return signBit or (biasedExp shl 23) or mantissa
}

/**
 * Returns the [Float] value corresponding to a given bit representation.
 */
@SinceKotlin("1.2")
public actual fun Float.Companion.fromBits(bits: Int): Float {
    val sign = if ((bits and Int.MIN_VALUE) != 0) -1.0 else 1.0
    val exp = (bits ushr 23) and 0xFF
    val mantissa = bits and 0x7FFFFF

    if (exp == 0xFF) {
        return if (mantissa == 0) (sign * Double.POSITIVE_INFINITY).toFloat() else Float.NaN
    }
    if (exp == 0) {
        if (mantissa == 0) return (sign * 0.0).toFloat()
        return (sign * mantissa.toDouble() * luaPow(2.0, -149).unsafeCast<Double>()).toFloat()
    }
    return (sign * (1.0 + mantissa.toDouble() / 8388608.0) * luaPow(2.0, exp - 127).unsafeCast<Double>()).toFloat()
}

/**
 * Counts the number of set bits in the binary representation of this [Int] number.
 */
@SinceKotlin("1.4")
public actual fun Int.countOneBits(): Int {
    var x = this - ((this ushr 1) and 0x55555555)
    x = (x and 0x33333333) + ((x ushr 2) and 0x33333333)
    return (((x + (x ushr 4)) and 0x0F0F0F0F) * 0x01010101) ushr 24
}

/**
 * Counts the number of consecutive most significant bits that are zero in the binary representation of this [Int] number.
 */
@SinceKotlin("1.4")
public actual fun Int.countLeadingZeroBits(): Int {
    if (this == 0) return 32
    var x = this
    var n = 0
    if ((x ushr 16) == 0) { n += 16; x = x shl 16 }
    if ((x ushr 24) == 0) { n += 8; x = x shl 8 }
    if ((x ushr 28) == 0) { n += 4; x = x shl 4 }
    if ((x ushr 30) == 0) { n += 2; x = x shl 2 }
    if ((x ushr 31) == 0) { n += 1 }
    return n
}

/**
 * Counts the number of consecutive least significant bits that are zero in the binary representation of this [Int] number.
 */
@SinceKotlin("1.4")
public actual fun Int.countTrailingZeroBits(): Int {
    if (this == 0) return 32
    var x = this
    var n = 0
    if ((x and 0xFFFF) == 0) { n += 16; x = x ushr 16 }
    if ((x and 0xFF) == 0) { n += 8; x = x ushr 8 }
    if ((x and 0xF) == 0) { n += 4; x = x ushr 4 }
    if ((x and 0x3) == 0) { n += 2; x = x ushr 2 }
    if ((x and 0x1) == 0) { n += 1 }
    return n
}

/**
 * Returns a number having a single bit set in the position of the most significant set bit of this [Int] number,
 * or zero, if this number is zero.
 */
@SinceKotlin("1.4")
public actual fun Int.takeHighestOneBit(): Int =
    if (this == 0) 0 else Int.MIN_VALUE ushr countLeadingZeroBits()

/**
 * Returns a number having a single bit set in the position of the least significant set bit of this [Int] number,
 * or zero, if this number is zero.
 */
@SinceKotlin("1.4")
public actual fun Int.takeLowestOneBit(): Int =
    this and -this

/**
 * Rotates the binary representation of this [Int] number left by the specified [bitCount] number of bits.
 * The most significant bits pushed out from the left side reenter the number as the least significant bits on the right side.
 *
 * Rotating the number left by a negative bit count is the same as rotating it right by the negated bit count:
 * `number.rotateLeft(-n) == number.rotateRight(n)`
 *
 * Rotating by a multiple of [Int.SIZE_BITS] (32) returns the same number, or more generally
 * `number.rotateLeft(n) == number.rotateLeft(n % 32)`
 */
@SinceKotlin("1.6")
public actual fun Int.rotateLeft(bitCount: Int): Int =
    (this shl bitCount) or (this ushr -bitCount)


/**
 * Rotates the binary representation of this [Int] number right by the specified [bitCount] number of bits.
 * The least significant bits pushed out from the right side reenter the number as the most significant bits on the left side.
 *
 * Rotating the number right by a negative bit count is the same as rotating it left by the negated bit count:
 * `number.rotateRight(-n) == number.rotateLeft(n)`
 *
 * Rotating by a multiple of [Int.SIZE_BITS] (32) returns the same number, or more generally
 * `number.rotateRight(n) == number.rotateRight(n % 32)`
 */
@SinceKotlin("1.6")
public actual fun Int.rotateRight(bitCount: Int): Int =
    (this ushr bitCount) or (this shl -bitCount)

private fun longLow32(v: Long): Int = v.toInt()

private fun longHigh32(v: Long): Int = (v ushr 32).toInt()

private fun longFromParts(hi: Int, lo: Int): Long =
    (hi.toLong() shl 32) or (lo.toLong() and 0xFFFFFFFFL)

/**
 * Counts the number of set bits in the binary representation of this [Long] number.
 */
@SinceKotlin("1.4")
public actual fun Long.countOneBits(): Int =
    longHigh32(this).countOneBits() + longLow32(this).countOneBits()

/**
 * Counts the number of consecutive most significant bits that are zero in the binary representation of this [Long] number.
 */
@SinceKotlin("1.4")
public actual fun Long.countLeadingZeroBits(): Int {
    val hi = longHigh32(this)
    return if (hi == 0) 32 + longLow32(this).countLeadingZeroBits() else hi.countLeadingZeroBits()
}

/**
 * Counts the number of consecutive least significant bits that are zero in the binary representation of this [Long] number.
 */
@SinceKotlin("1.4")
public actual fun Long.countTrailingZeroBits(): Int {
    val lo = longLow32(this)
    return if (lo == 0) 32 + longHigh32(this).countTrailingZeroBits() else lo.countTrailingZeroBits()
}

/**
 * Returns a number having a single bit set in the position of the most significant set bit of this [Long] number,
 * or zero, if this number is zero.
 */
@SinceKotlin("1.4")
public actual fun Long.takeHighestOneBit(): Long {
    if (this == 0L) return 0L
    val hi = longHigh32(this)
    return if (hi != 0) longFromParts(hi.takeHighestOneBit(), 0) else longFromParts(0, longLow32(this).takeHighestOneBit())
}

/**
 * Returns a number having a single bit set in the position of the least significant set bit of this [Long] number,
 * or zero, if this number is zero.
 */
@SinceKotlin("1.4")
public actual fun Long.takeLowestOneBit(): Long {
    if (this == 0L) return 0L
    val lo = longLow32(this)
    return if (lo != 0) longFromParts(0, lo.takeLowestOneBit()) else longFromParts(longHigh32(this).takeLowestOneBit(), 0)
}

/**
 * Rotates the binary representation of this [Long] number left by the specified [bitCount] number of bits.
 * The most significant bits pushed out from the left side reenter the number as the least significant bits on the right side.
 *
 * Rotating the number left by a negative bit count is the same as rotating it right by the negated bit count:
 * `number.rotateLeft(-n) == number.rotateRight(n)`
 *
 * Rotating by a multiple of [Long.SIZE_BITS] (64) returns the same number, or more generally
 * `number.rotateLeft(n) == number.rotateLeft(n % 64)`
 */
@SinceKotlin("1.6")
public actual fun Long.rotateLeft(bitCount: Int): Long =
    (this shl bitCount) or (this ushr -bitCount)

/**
 * Rotates the binary representation of this [Long] number right by the specified [bitCount] number of bits.
 * The least significant bits pushed out from the right side reenter the number as the most significant bits on the left side.
 *
 * Rotating the number right by a negative bit count is the same as rotating it left by the negated bit count:
 * `number.rotateRight(-n) == number.rotateLeft(n)`
 *
 * Rotating by a multiple of [Long.SIZE_BITS] (64) returns the same number, or more generally
 * `number.rotateRight(n) == number.rotateRight(n % 64)`
 */
@SinceKotlin("1.6")
public actual fun Long.rotateRight(bitCount: Int): Long =
    (this ushr bitCount) or (this shl -bitCount)
