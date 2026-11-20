@file:OptIn(LuaIntrinsic::class)

package kotlin.lua

import kotlin.internal.UsedFromCompilerGeneratedCode

private var objectHashCodes: dynamic = null
private var nextObjectHashCode: Int = 1

@UsedFromCompilerGeneratedCode
internal fun anyToString(o: Any?): String {
    if (luaEq(o, null)) {
        return "null"
    }
    return lua("tostring(o)")
}

@UsedFromCompilerGeneratedCode
internal fun toString(o: Any?): String {
    if (luaEq(o, null)) {
        return "null"
    }
    if (isTypeOf(o, "table")) {
        val mt: dynamic = luaGetMetatable(o)
        val t = o.asDynamic()
        if (luaNeq(mt, null) && luaNeq(mt.__types, null) && luaNeq(t.toString_k, null)) {
            return lua("t:toString_k()")
        }
    }
    return lua("tostring(o)")
}

@UsedFromCompilerGeneratedCode
internal fun equals(
    obj1: Any?,
    obj2: Any?,
): Boolean {
    if (luaEq(obj1, null)) {
        return luaEq(obj2, null)
    }
    if (luaEq(obj2, null)) {
        return false
    }
    if (luaNeq(obj1, obj1)) {
        return luaNeq(obj2, obj2)
    }
    if (isTypeOf(obj1, "number") && isTypeOf(obj2, "number")) {
        if (luaNeq(obj1, obj2)) {
            return false
        }
        if (luaEq(obj1, 0)) {
            return luaEq(luaDiv(1, obj1), luaDiv(1, obj2))
        }
        return true
    }
    if (isTypeOf(obj1, "table")) {
        val mt: dynamic = luaGetMetatable(obj1)
        val t = obj1.asDynamic()
        if (luaNeq(mt, null) && luaNeq(mt.__types, null) && luaNeq(t.equals_k, null)) {
            return lua("t:equals_k(obj2)")
        }
    }
    return luaEq(obj1, obj2)
}

@UsedFromCompilerGeneratedCode
internal fun anyHashCode(o: Any?): Int {
    if (luaEq(o, null)) {
        return 0
    }
    val t: dynamic = luaTypeOf(o)
    if (luaEq(t, "boolean")) {
        return if (luaEq(o, true)) 1231 else 1237
    }
    if (luaEq(t, "number")) {
        if (luaEq(o, 0)) {
            return 0
        }
        if (luaNeq(o, o)) {
            return 2146959360
        }
        if (luaEq(luaMod(o, 1), 0) && luaGte(o, -2147483648.0) && luaLte(o, 2147483647.0)) {
            return o.unsafeCast<Int>()
        }
        return lua(
            """
            (function()
                if o == 1 / 0 then return 2139095040 end
                if o == -1 / 0 then return -8388608 end
                local m, e = math.frexp(o)
                local hi = math.floor(m * 2147483648)
                local h = (hi * 31 + e) % 4294967296
                if h >= 2147483648 then return h - 4294967296 end
                return h
            end)()
            """
        )
    }
    if (luaEq(t, "string")) {
        return lua(
            """
            (function()
                local h = 0
                for i = 1, #o do
                    h = (h * 31 + string.byte(o, i)) % 4294967296
                end
                if h >= 2147483648 then return h - 4294967296 end
                return h
            end)()
            """
        )
    }
    if (luaEq(objectHashCodes, null)) {
        objectHashCodes = lua("setmetatable({}, { __mode = 'k' })")
        nextObjectHashCode = 1
    }
    var h: dynamic = luaTableGet(objectHashCodes, o)
    if (luaEq(h, null)) {
        h = nextObjectHashCode
        nextObjectHashCode = luaPlus(nextObjectHashCode, 1).unsafeCast<Int>()
        luaTableSet(objectHashCodes, o, h)
    }
    return h
}

@UsedFromCompilerGeneratedCode
internal fun hashCode(o: Any?): Int {
    if (luaEq(o, null)) {
        return 0
    }
    if (isTypeOf(o, "table")) {
        val mt: dynamic = luaGetMetatable(o)
        val t = o.asDynamic()
        if (luaNeq(mt, null) && luaNeq(mt.__types, null) && luaNeq(t.hashCode_k, null)) {
            return lua("t:hashCode_k()")
        }
    }
    return anyHashCode(o)
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

@UsedFromCompilerGeneratedCode
internal fun defineClass(
    superClass: dynamic,
    interfaces: dynamic,
): dynamic {
    val cls: dynamic = luaEmptyTable()
    cls.__index = cls
    val types: dynamic = luaEmptyTable()
    types[cls] = true
    if (luaNeq(superClass, null)) {
        lua(
            """
            for k, v in pairs(superClass.__types) do
                types[k] = v
            end
            """,
        )
    }
    if (luaNeq(interfaces, null)) {
        lua(
            """
            for i = 1, #interfaces do
                local iface = interfaces[i]
                for k, v in pairs(iface.__types) do
                    types[k] = v
                end
                for k, v in pairs(iface) do
                    if k ~= '__index' and k ~= '__types' and k ~= '__super' then
                        if cls[k] == nil and (superClass == nil or superClass[k] == nil) then
                            cls[k] = v
                        end
                    end
                end
            end
            """,
        )
    }
    cls.__types = types
    cls.__super = superClass
    if (luaNeq(superClass, null)) {
        val mt: dynamic = luaEmptyTable()
        mt.__index = superClass
        luaSetMetatable(cls, mt)
    }
    return cls
}

@UsedFromCompilerGeneratedCode
internal fun isInstance(
    obj: Any?,
    targetClass: Any?,
): Boolean {
    if (luaEq(obj, null) || luaEq(targetClass, null)) {
        return false
    }
    if (!isTypeOf(obj, "table")) {
        return false
    }
    val mt: dynamic = luaGetMetatable(obj)
    if (luaEq(mt, null)) {
        return false
    }
    val types: dynamic = mt.__types
    return !luaEq(types, null) && luaEq(luaTableGet(types, targetClass), true)
}

@UsedFromCompilerGeneratedCode
internal fun isInt(o: Any?): Boolean =
    isTypeOf(o, "number") &&
            luaEq(luaMod(o, 1), 0) &&
            luaGte(o, -2147483648.0) &&
            luaLte(o, 2147483647.0)

@UsedFromCompilerGeneratedCode
internal fun isByte(o: Any?): Boolean =
    isTypeOf(o, "number") &&
            luaEq(luaMod(o, 1), 0) &&
            luaGte(o, -128.0) &&
            luaLte(o, 127.0)

@UsedFromCompilerGeneratedCode
internal fun isShort(o: Any?): Boolean =
    isTypeOf(o, "number") &&
            luaEq(luaMod(o, 1), 0) &&
            luaGte(o, -32768.0) &&
            luaLte(o, 32767.0)