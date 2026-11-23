@file:OptIn(LuaIntrinsic::class)

package kotlin.lua

import kotlin.internal.UsedFromCompilerGeneratedCode

private const val TWO_32 = 4294967296.0
private const val TWO_31 = 2147483648.0
private const val TWO_16 = 65536.0
private const val TWO_63 = 9223372036854775808.0

private val LONG_MT: Any = luaEmptyTable()

private fun makeLong(lo: Double, hi: Int): Long {
    val t: Any = luaEmptyTable()
    luaTableSet(t, 1, lo)
    luaTableSet(t, 2, hi)
    luaSetMetatable(t, LONG_MT)
    return t.unsafeCast<Long>()
}

private inline fun loOf(v: Long): Double = luaTableGet(v, 1).unsafeCast<Double>()

private inline fun hiOf(v: Long): Int = luaTableGet(v, 2).unsafeCast<Int>()

private val LONG_ZERO: Long = makeLong(0.0, 0)
private val LONG_ONE: Long = makeLong(1.0, 0)
private val LONG_MAX: Long = makeLong(4294967295.0, 2147483647)
private val LONG_MIN: Long = makeLong(0.0, -2147483648)

@UsedFromCompilerGeneratedCode
internal fun isLong(o: Any?): Boolean =
    isTypeOf(o, "table") && luaEq(luaGetMetatable(o), LONG_MT)

@UsedFromCompilerGeneratedCode
internal fun longFromTwoInts(low: Int, high: Int): Long =
    makeLong(luaMod(low, TWO_32).unsafeCast<Double>(), toInt32(high))

@UsedFromCompilerGeneratedCode
internal fun longFromInt(v: Int): Long {
    val iv = toInt32(v)
    return makeLong(
        luaMod(iv, TWO_32).unsafeCast<Double>(),
        if (luaLt(iv, 0)) -1 else 0,
    )
}

@UsedFromCompilerGeneratedCode
internal fun charToLong(c: Char): Long = longFromInt(charToInt(c))

@UsedFromCompilerGeneratedCode
internal fun numberToLong(v: Double): Long {
    if (luaNeq(v, v)) return LONG_ZERO
    if (luaGte(v, TWO_63)) return LONG_MAX
    if (luaLte(v, luaUnaryMinus(TWO_63))) return LONG_MIN

    val trunc: Double =
        if (luaGte(v, 0.0)) {
            LuaMath.floor(v).unsafeCast<Double>()
        } else {
            LuaMath.ceil(v).unsafeCast<Double>()
        }
    val isNeg = luaLt(trunc, 0.0)
    val absVal: Double = if (isNeg) luaUnaryMinus(trunc).unsafeCast<Double>() else trunc
    val hi: Int = LuaMath.floor(luaDiv(absVal, TWO_32)).unsafeCast<Int>()
    val lo: Double = luaMinus(absVal, luaMult(hi, TWO_32)).unsafeCast<Double>()
    val res = makeLong(lo, hi)
    return if (isNeg) longNegate(res) else res
}

@UsedFromCompilerGeneratedCode
internal fun longToInt(v: Long): Int = toInt32(loOf(v))

@UsedFromCompilerGeneratedCode
internal fun longToByte(v: Long): Byte = intToByte(longToInt(v))

@UsedFromCompilerGeneratedCode
internal fun longToShort(v: Long): Short = intToShort(longToInt(v))

@UsedFromCompilerGeneratedCode
internal fun longToChar(v: Long): Char = intToChar(longToInt(v))

@UsedFromCompilerGeneratedCode
internal fun longToNumber(v: Long): Double =
    luaPlus(luaMult(hiOf(v), TWO_32), loOf(v)).unsafeCast<Double>()

@UsedFromCompilerGeneratedCode
internal fun dynamicNumberToByte(v: Any?): Byte =
    if (isLong(v)) longToByte(v.unsafeCast<Long>()) else numberToByte(v)

@UsedFromCompilerGeneratedCode
internal fun dynamicNumberToShort(v: Any?): Short =
    if (isLong(v)) longToShort(v.unsafeCast<Long>()) else numberToShort(v)

@UsedFromCompilerGeneratedCode
internal fun dynamicNumberToInt(v: Any?): Int =
    if (isLong(v)) longToInt(v.unsafeCast<Long>()) else numberToInt(v)

@UsedFromCompilerGeneratedCode
internal fun dynamicNumberToLong(v: Any?): Long =
    if (isLong(v)) v.unsafeCast<Long>() else numberToLong(v.unsafeCast<Double>())

@UsedFromCompilerGeneratedCode
internal fun dynamicNumberToDouble(v: Any?): Double =
    if (isLong(v)) longToNumber(v.unsafeCast<Long>()) else v.unsafeCast<Double>()

@UsedFromCompilerGeneratedCode
internal fun dynamicNumberToChar(v: Any?): Char =
    if (isLong(v)) longToChar(v.unsafeCast<Long>()) else numberToChar(v)

@UsedFromCompilerGeneratedCode
internal fun longEquals(a: Long, b: Long): Boolean =
    luaEq(loOf(a), loOf(b)) && luaEq(hiOf(a), hiOf(b))

@UsedFromCompilerGeneratedCode
internal fun longHashCode(a: Long): Int =
    bitXor(hiOf(a), toInt32(loOf(a)))

@UsedFromCompilerGeneratedCode
internal fun longCompare(a: Long, b: Long): Int {
    val aHi = hiOf(a)
    val bHi = hiOf(b)
    if (luaLt(aHi, bHi)) return -1
    if (luaGt(aHi, bHi)) return 1
    val aLo = loOf(a)
    val bLo = loOf(b)
    if (luaLt(aLo, bLo)) return -1
    if (luaGt(aLo, bLo)) return 1
    return 0
}

@UsedFromCompilerGeneratedCode
internal fun longLt(a: Long, b: Long): Boolean = luaLt(longCompare(a, b), 0)

@UsedFromCompilerGeneratedCode
internal fun longLte(a: Long, b: Long): Boolean = luaLte(longCompare(a, b), 0)

@UsedFromCompilerGeneratedCode
internal fun longGt(a: Long, b: Long): Boolean = luaGt(longCompare(a, b), 0)

@UsedFromCompilerGeneratedCode
internal fun longGte(a: Long, b: Long): Boolean = luaGte(longCompare(a, b), 0)

@UsedFromCompilerGeneratedCode
internal fun longNegate(a: Long): Long {
    val aLo = loOf(a)
    val aHi = hiOf(a)
    if (luaEq(aLo, 0.0)) {
        return makeLong(0.0, toInt32(luaUnaryMinus(aHi)))
    }
    return makeLong(
        luaMinus(TWO_32, aLo).unsafeCast<Double>(),
        toInt32(luaMinus(luaUnaryMinus(aHi), 1)),
    )
}

@UsedFromCompilerGeneratedCode
internal fun longInc(a: Long): Long = longAdd(a, LONG_ONE)

@UsedFromCompilerGeneratedCode
internal fun longDec(a: Long): Long = longSubtract(a, LONG_ONE)

@UsedFromCompilerGeneratedCode
internal fun longAdd(a: Long, b: Long): Long {
    val sumLo: Double = luaPlus(loOf(a), loOf(b)).unsafeCast<Double>()
    val carry: Int = if (luaGte(sumLo, TWO_32)) 1 else 0
    val lo: Double = if (luaNeq(carry, 0)) luaMinus(sumLo, TWO_32).unsafeCast<Double>() else sumLo
    val hi: Int = toInt32(luaPlus(luaPlus(hiOf(a), hiOf(b)), carry))
    return makeLong(lo, hi)
}

@UsedFromCompilerGeneratedCode
internal fun longSubtract(a: Long, b: Long): Long {
    val diffLo: Double = luaMinus(loOf(a), loOf(b)).unsafeCast<Double>()
    val borrow: Int = if (luaLt(diffLo, 0.0)) 1 else 0
    val lo: Double = if (luaNeq(borrow, 0)) luaPlus(diffLo, TWO_32).unsafeCast<Double>() else diffLo
    val hi: Int = toInt32(luaMinus(luaMinus(hiOf(a), hiOf(b)), borrow))
    return makeLong(lo, hi)
}

@UsedFromCompilerGeneratedCode
internal fun longMultiply(a: Long, b: Long): Long {
    val aLo = loOf(a)
    val bLo = loOf(b)
    val aHiU: Double = luaMod(hiOf(a), TWO_32).unsafeCast<Double>()
    val bHiU: Double = luaMod(hiOf(b), TWO_32).unsafeCast<Double>()

    val a0: Double = luaMod(aLo, TWO_16).unsafeCast<Double>()
    val a1: Double = LuaMath.floor(luaDiv(aLo, TWO_16)).unsafeCast<Double>()
    val a2: Double = luaMod(aHiU, TWO_16).unsafeCast<Double>()
    val a3: Double = LuaMath.floor(luaDiv(aHiU, TWO_16)).unsafeCast<Double>()

    val b0: Double = luaMod(bLo, TWO_16).unsafeCast<Double>()
    val b1: Double = LuaMath.floor(luaDiv(bLo, TWO_16)).unsafeCast<Double>()
    val b2: Double = luaMod(bHiU, TWO_16).unsafeCast<Double>()
    val b3: Double = LuaMath.floor(luaDiv(bHiU, TWO_16)).unsafeCast<Double>()

    val c0: Double = luaMult(a0, b0).unsafeCast<Double>()
    val r0: Double = luaMod(c0, TWO_16).unsafeCast<Double>()
    val c1: Double = luaPlus(luaPlus(luaMult(a1, b0), luaMult(a0, b1)), LuaMath.floor(luaDiv(c0, TWO_16))).unsafeCast<Double>()
    val r1: Double = luaMod(c1, TWO_16).unsafeCast<Double>()
    val c2: Double = luaPlus(
        luaPlus(luaPlus(luaMult(a2, b0), luaMult(a1, b1)), luaMult(a0, b2)),
        LuaMath.floor(luaDiv(c1, TWO_16)),
    ).unsafeCast<Double>()
    val r2: Double = luaMod(c2, TWO_16).unsafeCast<Double>()
    val c3: Double = luaPlus(
        luaPlus(luaPlus(luaPlus(luaMult(a3, b0), luaMult(a2, b1)), luaMult(a1, b2)), luaMult(a0, b3)),
        LuaMath.floor(luaDiv(c2, TWO_16)),
    ).unsafeCast<Double>()
    val r3: Double = luaMod(c3, TWO_16).unsafeCast<Double>()

    val lo: Double = luaPlus(luaMult(r1, TWO_16), r0).unsafeCast<Double>()
    val hi: Int = toInt32(luaPlus(luaMult(r3, TWO_16), r2))
    return makeLong(lo, hi)
}

private fun cmpUnsigned64(aLo: Double, aHiU: Double, bLo: Double, bHiU: Double): Int {
    if (luaLt(aHiU, bHiU)) return -1
    if (luaGt(aHiU, bHiU)) return 1
    if (luaLt(aLo, bLo)) return -1
    if (luaGt(aLo, bLo)) return 1
    return 0
}

private fun divModUnsigned64(
    aLo: Double,
    aHiU: Double,
    bLo: Double,
    bHiU: Double,
    wantRemainder: Boolean,
): Long {
    if (luaLt(aHiU, 2097152.0) && luaLt(bHiU, 2097152.0)) {
        val va: Double = luaPlus(luaMult(aHiU, TWO_32), aLo).unsafeCast<Double>()
        val vb: Double = luaPlus(luaMult(bHiU, TWO_32), bLo).unsafeCast<Double>()
        val q: Double = LuaMath.floor(luaDiv(va, vb)).unsafeCast<Double>()
        val resVal: Double = if (wantRemainder) luaMinus(va, luaMult(q, vb)).unsafeCast<Double>() else q
        val resHiU: Double = LuaMath.floor(luaDiv(resVal, TWO_32)).unsafeCast<Double>()
        val resLo: Double = luaMinus(resVal, luaMult(resHiU, TWO_32)).unsafeCast<Double>()
        return makeLong(resLo, toInt32(resHiU))
    }

    var qLo = 0.0
    var qHiU = 0.0
    var rLo = 0.0
    var rHiU = 0.0

    var i = 63
    while (luaGte(i, 0)) {
        val carryBit: Double = if (luaGte(rLo, TWO_31)) 1.0 else 0.0
        rHiU = luaMod(luaPlus(luaMult(rHiU, 2.0), carryBit), TWO_32).unsafeCast<Double>()
        rLo = luaMod(luaMult(rLo, 2.0), TWO_32).unsafeCast<Double>()

        val bit: Double =
            if (luaGte(i, 32)) {
                luaMod(LuaMath.floor(luaDiv(aHiU, luaPow(2.0, luaMinus(i, 32)))), 2.0).unsafeCast<Double>()
            } else {
                luaMod(LuaMath.floor(luaDiv(aLo, luaPow(2.0, i))), 2.0).unsafeCast<Double>()
            }
        rLo = luaPlus(rLo, bit).unsafeCast<Double>()

        if (luaGte(cmpUnsigned64(rLo, rHiU, bLo, bHiU), 0)) {
            val diffLo: Double = luaMinus(rLo, bLo).unsafeCast<Double>()
            val borrow: Double = if (luaLt(diffLo, 0.0)) 1.0 else 0.0
            rLo = if (luaNeq(borrow, 0.0)) luaPlus(diffLo, TWO_32).unsafeCast<Double>() else diffLo
            rHiU = luaMinus(luaMinus(rHiU, bHiU), borrow).unsafeCast<Double>()

            if (luaGte(i, 32)) {
                qHiU = luaPlus(qHiU, luaPow(2.0, luaMinus(i, 32))).unsafeCast<Double>()
            } else {
                qLo = luaPlus(qLo, luaPow(2.0, i)).unsafeCast<Double>()
            }
        }
        i = luaMinus(i, 1).unsafeCast<Int>()
    }

    return if (wantRemainder) {
        makeLong(rLo, toInt32(rHiU))
    } else {
        makeLong(qLo, toInt32(qHiU))
    }
}

@UsedFromCompilerGeneratedCode
internal fun longDivide(a: Long, b: Long): Long {
    val bLo = loOf(b)
    val bHi = hiOf(b)
    if (luaEq(bLo, 0.0) && luaEq(bHi, 0)) luaError("/ by zero")
    val aLo = loOf(a)
    val aHi = hiOf(a)
    if (luaEq(aHi, -2147483648) && luaEq(aLo, 0.0) && luaEq(bHi, -1) && luaEq(bLo, 4294967295.0)) {
        return LONG_MIN
    }
    val aNeg = luaLt(aHi, 0)
    val bNeg = luaLt(bHi, 0)
    val absA = if (aNeg) longNegate(a) else a
    val absB = if (bNeg) longNegate(b) else b
    val q = divModUnsigned64(
        loOf(absA),
        luaMod(hiOf(absA), TWO_32).unsafeCast<Double>(),
        loOf(absB),
        luaMod(hiOf(absB), TWO_32).unsafeCast<Double>(),
        wantRemainder = false,
    )
    return if (luaNeq(aNeg, bNeg)) longNegate(q) else q
}

@UsedFromCompilerGeneratedCode
internal fun longModulo(a: Long, b: Long): Long {
    val bLo = loOf(b)
    val bHi = hiOf(b)
    if (luaEq(bLo, 0.0) && luaEq(bHi, 0)) luaError("/ by zero")
    val aLo = loOf(a)
    val aHi = hiOf(a)
    if (luaEq(aHi, -2147483648) && luaEq(aLo, 0.0) && luaEq(bHi, -1) && luaEq(bLo, 4294967295.0)) {
        return LONG_ZERO
    }
    val aNeg = luaLt(aHi, 0)
    val absA = if (aNeg) longNegate(a) else a
    val absB = if (luaLt(bHi, 0)) longNegate(b) else b
    val r = divModUnsigned64(
        loOf(absA),
        luaMod(hiOf(absA), TWO_32).unsafeCast<Double>(),
        loOf(absB),
        luaMod(hiOf(absB), TWO_32).unsafeCast<Double>(),
        wantRemainder = true,
    )
    return if (aNeg) longNegate(r) else r
}

@UsedFromCompilerGeneratedCode
internal fun longAnd(a: Long, b: Long): Long =
    makeLong(
        luaMod(bitAnd(toInt32(loOf(a)), toInt32(loOf(b))), TWO_32).unsafeCast<Double>(),
        bitAnd(hiOf(a), hiOf(b)),
    )

@UsedFromCompilerGeneratedCode
internal fun longOr(a: Long, b: Long): Long =
    makeLong(
        luaMod(bitOr(toInt32(loOf(a)), toInt32(loOf(b))), TWO_32).unsafeCast<Double>(),
        bitOr(hiOf(a), hiOf(b)),
    )

@UsedFromCompilerGeneratedCode
internal fun longXor(a: Long, b: Long): Long =
    makeLong(
        luaMod(bitXor(toInt32(loOf(a)), toInt32(loOf(b))), TWO_32).unsafeCast<Double>(),
        bitXor(hiOf(a), hiOf(b)),
    )

@UsedFromCompilerGeneratedCode
internal fun longInv(a: Long): Long =
    makeLong(luaMinus(4294967295.0, loOf(a)).unsafeCast<Double>(), bitNot(hiOf(a)))

@UsedFromCompilerGeneratedCode
internal fun longShiftLeft(a: Long, bitCount: Int): Long {
    val s = bitAnd(bitCount, 63)
    if (luaEq(s, 0)) return a
    val aLo = loOf(a)
    val aHi = hiOf(a)
    if (luaLt(s, 32)) {
        val p: Double = luaPow(2.0, s).unsafeCast<Double>()
        val shiftedLo: Double = luaMult(aLo, p).unsafeCast<Double>()
        val newLo: Double = luaMod(shiftedLo, TWO_32).unsafeCast<Double>()
        val carry: Double = LuaMath.floor(luaDiv(shiftedLo, TWO_32)).unsafeCast<Double>()
        val newHi: Int = toInt32(luaPlus(luaMult(aHi, p), carry))
        return makeLong(newLo, newHi)
    }
    val p: Double = luaPow(2.0, luaMinus(s, 32)).unsafeCast<Double>()
    return makeLong(0.0, toInt32(luaMult(aLo, p)))
}

@UsedFromCompilerGeneratedCode
internal fun longShiftRight(a: Long, bitCount: Int): Long {
    val s = bitAnd(bitCount, 63)
    if (luaEq(s, 0)) return a
    val aLo = loOf(a)
    val aHi = hiOf(a)
    if (luaLt(s, 32)) {
        val p: Double = luaPow(2.0, s).unsafeCast<Double>()
        val invP: Double = luaPow(2.0, luaMinus(32, s)).unsafeCast<Double>()
        val newHi: Int = LuaMath.floor(luaDiv(aHi, p)).unsafeCast<Int>()
        val hiRem: Double = luaMod(aHi, p).unsafeCast<Double>()
        val newLo: Double = luaPlus(LuaMath.floor(luaDiv(aLo, p)), luaMult(hiRem, invP)).unsafeCast<Double>()
        return makeLong(newLo, newHi)
    }
    val p: Double = luaPow(2.0, luaMinus(s, 32)).unsafeCast<Double>()
    val newLo: Double = luaMod(LuaMath.floor(luaDiv(aHi, p)), TWO_32).unsafeCast<Double>()
    val newHi: Int = if (luaLt(aHi, 0)) -1 else 0
    return makeLong(newLo, newHi)
}

@UsedFromCompilerGeneratedCode
internal fun longShiftRightUnsigned(a: Long, bitCount: Int): Long {
    val s = bitAnd(bitCount, 63)
    if (luaEq(s, 0)) return a
    val aLo = loOf(a)
    val hiU: Double = luaMod(hiOf(a), TWO_32).unsafeCast<Double>()
    if (luaLt(s, 32)) {
        val p: Double = luaPow(2.0, s).unsafeCast<Double>()
        val invP: Double = luaPow(2.0, luaMinus(32, s)).unsafeCast<Double>()
        val hiRem: Double = luaMod(hiU, p).unsafeCast<Double>()
        val newLo: Double = luaPlus(LuaMath.floor(luaDiv(aLo, p)), luaMult(hiRem, invP)).unsafeCast<Double>()
        val newHi: Int = toInt32(LuaMath.floor(luaDiv(hiU, p)))
        return makeLong(newLo, newHi)
    }
    val p: Double = luaPow(2.0, luaMinus(s, 32)).unsafeCast<Double>()
    return makeLong(LuaMath.floor(luaDiv(hiU, p)).unsafeCast<Double>(), 0)
}

@UsedFromCompilerGeneratedCode
internal fun longToString(a: Long): String {
    val aLo = loOf(a)
    val aHi = hiOf(a)
    if (luaEq(aHi, 0) && luaEq(aLo, 0.0)) return "0"
    val isNeg = luaLt(aHi, 0)
    val absVal = if (isNeg) longNegate(a) else a
    var curLo: Double = loOf(absVal)
    var curHiU: Double = luaMod(hiOf(absVal), TWO_32).unsafeCast<Double>()

    val base = 100000.0
    var result = ""
    while (luaGt(curHiU, 0.0) || luaGt(curLo, 0.0)) {
        val hiQ: Double = LuaMath.floor(luaDiv(curHiU, base)).unsafeCast<Double>()
        val hiR: Double = luaMinus(curHiU, luaMult(hiQ, base)).unsafeCast<Double>()
        val mid: Double = luaPlus(luaMult(hiR, TWO_32), curLo).unsafeCast<Double>()
        val loQ: Double = LuaMath.floor(luaDiv(mid, base)).unsafeCast<Double>()
        val rem: Int = luaMinus(mid, luaMult(loQ, base)).unsafeCast<Int>()
        curHiU = hiQ
        curLo = loQ
        val hasMore = luaGt(curHiU, 0.0) || luaGt(curLo, 0.0)
        val chunk: String = LuaString.format(if (hasMore) "%05d" else "%d", rem)
        result = luaConcat(chunk, result)
    }
    return if (isNeg) luaConcat("-", result) else result
}

@UsedFromCompilerGeneratedCode
internal fun longRangeToLong(start: Long, endInclusive: Long): LongRange =
    LongRange(start, endInclusive)
