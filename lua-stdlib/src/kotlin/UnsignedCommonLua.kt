@file:OptIn(LuaIntrinsic::class)

package kotlin

import kotlin.lua.LuaIntrinsic
import kotlin.lua.lua
import kotlin.lua.luaMinus
import kotlin.lua.luaMod
import kotlin.lua.luaPlus

@PublishedApi
internal actual fun uintRemainder(v1: UInt, v2: UInt): UInt {
    val i2 = v2.toInt()
    if (i2 == 0) throw RuntimeException("Division by zero")
    val u1 = uintToDouble(v1.toInt())
    val u2 = uintToDouble(i2)
    val r: Double = luaMod(u1, u2).unsafeCast<Double>()
    return doubleToUInt(r)
}

@PublishedApi
internal actual fun ulongRemainder(v1: ULong, v2: ULong): ULong {
    val l2 = v2.toLong()
    if (l2 == 0L) throw RuntimeException("Division by zero")
    val u1 = ulongToDouble(v1.toLong())
    val u2 = ulongToDouble(l2)
    val r: Double = luaMod(u1, u2).unsafeCast<Double>()
    return doubleToULong(r)
}

@PublishedApi
internal actual fun uintDivide(v1: UInt, v2: UInt): UInt {
    val i2 = v2.toInt()
    if (i2 == 0) throw RuntimeException("Division by zero")
    val u1 = uintToDouble(v1.toInt())
    val u2 = uintToDouble(i2)
    val q: Double = lua("math.floor(u1 / u2)")
    return doubleToUInt(q)
}

@PublishedApi
internal actual fun ulongDivide(v1: ULong, v2: ULong): ULong {
    val l2 = v2.toLong()
    if (l2 == 0L) throw RuntimeException("Division by zero")
    val u1 = ulongToDouble(v1.toLong())
    val u2 = ulongToDouble(l2)
    val q: Double = lua("math.floor(u1 / u2)")
    return doubleToULong(q)
}

@PublishedApi
internal actual fun uintCompare(v1: Int, v2: Int): Int {
    val x = v1 xor Int.MIN_VALUE
    val y = v2 xor Int.MIN_VALUE
    return if (x < y) -1 else if (x > y) 1 else 0
}

@PublishedApi
internal actual fun ulongCompare(v1: Long, v2: Long): Int {
    val neg1 = v1 < 0L
    val neg2 = v2 < 0L
    if (neg1 != neg2) return if (neg1) 1 else -1
    return if (v1 < v2) -1 else if (v1 > v2) 1 else 0
}

@PublishedApi
internal actual fun uintToULong(value: Int): ULong =
    uintToLong(value).toULong()

@PublishedApi
internal actual fun uintToLong(value: Int): Long =
    uintToDouble(value).unsafeCast<Long>()

@PublishedApi
internal actual fun uintToFloat(value: Int): Float =
    uintToDouble(value).toFloat()

@PublishedApi
internal actual fun floatToUInt(value: Float): UInt =
    doubleToUInt(value.toDouble())

@PublishedApi
internal actual fun uintToDouble(value: Int): Double =
    if (value < 0) value.toDouble() + 4294967296.0 else value.toDouble()

@PublishedApi
internal actual fun doubleToUInt(value: Double): UInt {
    if (value.isNaN() || value <= 0.0) return 0u
    if (value >= 4294967295.0) return UInt.MAX_VALUE
    val f: Double = lua("math.floor(value)")
    val signed = if (f >= 2147483648.0) (f - 4294967296.0).toInt() else f.toInt()
    return signed.toUInt()
}

@PublishedApi
internal actual fun ulongToFloat(value: Long): Float =
    ulongToDouble(value).toFloat()

@PublishedApi
internal actual fun floatToULong(value: Float): ULong =
    doubleToULong(value.toDouble())

@PublishedApi
internal actual fun ulongToDouble(value: Long): Double =
    if (value < 0L) {
        luaPlus(value, 18446744073709551616.0).unsafeCast<Double>()
    } else {
        value.unsafeCast<Double>()
    }

@PublishedApi
internal actual fun doubleToULong(value: Double): ULong {
    if (value.isNaN() || value <= 0.0) return 0uL
    if (value >= 18446744073709551615.0) return ULong.MAX_VALUE
    val f: Double = lua("math.floor(value)")
    val signed: Long =
        if (f >= 9223372036854775808.0) {
            luaMinus(f, 18446744073709551616.0).unsafeCast<Long>()
        } else {
            f.unsafeCast<Long>()
        }
    return signed.toULong()
}

internal actual fun uintToString(value: Int): String =
    uintToString(value, 10)

internal actual fun uintToString(value: Int, base: Int): String {
    kotlin.text.checkRadix(base)
    if (value >= 0) return value.toString(base)
    return uintToLong(value).toString(base)
}

internal actual fun ulongToString(value: Long): String =
    ulongToString(value, 10)

internal actual fun ulongToString(value: Long, base: Int): String {
    kotlin.text.checkRadix(base)
    if (value >= 0L) return value.toString(base)
    val u = value.toULong()
    val baseU = base.toULong()
    val q = ulongDivide(u, baseU)
    val r = ulongRemainder(u, baseU).toInt()
    val digit = if (r < 10) ('0' + r) else ('a' + (r - 10))
    return if (q == 0uL) digit.toString() else ulongToString(q.toLong(), base) + digit
}
