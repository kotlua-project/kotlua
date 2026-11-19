@file:OptIn(LuaIntrinsic::class)

package kotlin.lua

import kotlin.internal.UsedFromCompilerGeneratedCode

@UsedFromCompilerGeneratedCode
internal fun equals(obj1: Any?, obj2: Any?): Boolean {
    if (luaEq(obj1, null)) {
        return luaEq(obj2, null)
    }
    if (luaEq(obj2, null)) {
        return false
    }
    if (luaNeq(obj1, obj1)) {
        return luaNeq(obj2, obj2)
    }
    if (luaEq(lua("type(obj1)"), "number") && luaEq(lua("type(obj2)"), "number")) {
        if (luaNeq(obj1, obj2)) {
            return false
        }
        if (luaEq(obj1, 0)) {
            return luaEq(luaDiv(1, obj1), luaDiv(1, obj2))
        }
        return true
    }
    return luaEq(obj1, obj2)
}

@UsedFromCompilerGeneratedCode
internal fun toString(o: Any?): String {
    if (luaEq(o, null)) {
        return "null"
    }
    return lua("tostring(o)")
}

@UsedFromCompilerGeneratedCode
internal fun intDiv(a: Int, b: Int): Int {
    val q = luaDiv(a, b)
    return if (luaGte(q, 0)) {
        lua("math.floor(q)")
    } else {
        lua("math.ceil(q)")
    }
}

@UsedFromCompilerGeneratedCode
internal fun rem(a: Any?, b: Any?): Any? = lua("math.fmod(a, b)")

private fun toInt32(u: dynamic): Int =
    if (luaGte(u, 2147483648.0)) {
        luaMinus(u, 4294967296.0)
    } else {
        u
    }

@UsedFromCompilerGeneratedCode
internal fun bitAnd(
    a: Int,
    b: Int,
): Int {
    var ua: dynamic = luaMod(a, 4294967296.0)
    var ub: dynamic = luaMod(b, 4294967296.0)
    var res: dynamic = 0
    var bit: dynamic = 1
    while (luaGt(ua, 0) && luaGt(ub, 0)) {
        val ra: dynamic = luaMod(ua, 2)
        val rb: dynamic = luaMod(ub, 2)
        if (luaEq(ra, 1) && luaEq(rb, 1)) {
            res = luaPlus(res, bit)
        }
        ua = luaDiv(luaMinus(ua, ra), 2)
        ub = luaDiv(luaMinus(ub, rb), 2)
        bit = luaMult(bit, 2)
    }
    return toInt32(res)
}

@UsedFromCompilerGeneratedCode
internal fun bitOr(
    a: Int,
    b: Int,
): Int {
    var ua: dynamic = luaMod(a, 4294967296.0)
    var ub: dynamic = luaMod(b, 4294967296.0)
    var res: dynamic = 0
    var bit: dynamic = 1
    while (luaGt(ua, 0) || luaGt(ub, 0)) {
        val ra: dynamic = luaMod(ua, 2)
        val rb: dynamic = luaMod(ub, 2)
        if (luaEq(ra, 1) || luaEq(rb, 1)) {
            res = luaPlus(res, bit)
        }
        ua = luaDiv(luaMinus(ua, ra), 2)
        ub = luaDiv(luaMinus(ub, rb), 2)
        bit = luaMult(bit, 2)
    }
    return toInt32(res)
}

@UsedFromCompilerGeneratedCode
internal fun bitXor(
    a: Int,
    b: Int,
): Int {
    var ua: dynamic = luaMod(a, 4294967296.0)
    var ub: dynamic = luaMod(b, 4294967296.0)
    var res: dynamic = 0
    var bit: dynamic = 1
    while (luaGt(ua, 0) || luaGt(ub, 0)) {
        val ra: dynamic = luaMod(ua, 2)
        val rb: dynamic = luaMod(ub, 2)
        if (luaNeq(ra, rb)) {
            res = luaPlus(res, bit)
        }
        ua = luaDiv(luaMinus(ua, ra), 2)
        ub = luaDiv(luaMinus(ub, rb), 2)
        bit = luaMult(bit, 2)
    }
    return toInt32(res)
}

@UsedFromCompilerGeneratedCode
internal fun bitNot(a: Int): Int {
    val u: dynamic = luaMinus(4294967295.0, luaMod(a, 4294967296.0))
    return toInt32(u)
}

@UsedFromCompilerGeneratedCode
internal fun bitShl(
    a: Int,
    bitCount: Int,
): Int {
    val s: dynamic = luaMod(bitCount, 32)
    val u: dynamic = luaMod(luaMult(a, luaPow(2, s)), 4294967296.0)
    return toInt32(u)
}

@UsedFromCompilerGeneratedCode
internal fun bitShr(
    a: Int,
    bitCount: Int,
): Int {
    val s: dynamic = luaMod(bitCount, 32)
    val q: dynamic = luaDiv(a, luaPow(2, s))
    val u: dynamic = luaMod(lua("math.floor(q)"), 4294967296.0)
    return toInt32(u)
}

@UsedFromCompilerGeneratedCode
internal fun bitUshr(
    a: Int,
    bitCount: Int,
): Int {
    val s: dynamic = luaMod(bitCount, 32)
    val q: dynamic = luaDiv(luaMod(a, 4294967296.0), luaPow(2, s))
    val u: dynamic = lua("math.floor(q)")
    return toInt32(u)
}