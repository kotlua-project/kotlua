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
        if (isLong(o)) return longToString(o.unsafeCast<Long>())
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
        if (isLong(obj1)) {
            return isLong(obj2) && longEquals(obj1.unsafeCast<Long>(), obj2.unsafeCast<Long>())
        }
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
    if (isLong(o)) return longHashCode(o.unsafeCast<Long>())
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
internal fun compareTo(
    a: Any?,
    b: Any?,
): Int {
    if (isTypeOf(a, "number") && isTypeOf(b, "number")) {
        if (luaLt(a, b)) {
            return -1
        }
        if (luaGt(a, b)) {
            return 1
        }
        if (luaEq(a, b)) {
            if (luaEq(a, 0)) {
                val invA: dynamic = luaDiv(1, a)
                val invB: dynamic = luaDiv(1, b)
                if (luaLt(invA, invB)) {
                    return -1
                }
                if (luaGt(invA, invB)) {
                    return 1
                }
            }
            return 0
        }
        if (luaNeq(a, a)) {
            return if (luaNeq(b, b)) 0 else 1
        }
        return -1
    }
    if (isTypeOf(a, "string") && isTypeOf(b, "string")) {
        if (luaLt(a, b)) {
            return -1
        }
        if (luaGt(a, b)) {
            return 1
        }
        return 0
    }
    if (isTypeOf(a, "boolean") && isTypeOf(b, "boolean")) {
        if (luaEq(a, b)) {
            return 0
        }
        return if (luaEq(a, true)) 1 else -1
    }
    if (isLong(a) && isLong(b)) {
        return longCompare(a.unsafeCast<Long>(), b.unsafeCast<Long>())
    }
    if (isTypeOf(a, "table")) {
        val mt: dynamic = luaGetMetatable(a)
        val t = a.asDynamic()
        if (luaNeq(mt, null) && luaNeq(mt.__types, null) && luaNeq(t.compareTo_k, null)) {
            return lua("t:compareTo_k(b)")
        }
    }
    luaError("Cannot compare values")
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
        LuaMath.floor(q)
    } else {
        LuaMath.ceil(q)
    }
}

@UsedFromCompilerGeneratedCode
internal fun rem(a: Any?, b: Any?): Any = LuaMath.fmod(a, b)

internal fun toInt32(u: dynamic): Int =
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
    val u: dynamic = luaMod(LuaMath.floor(q), 4294967296.0)
    return toInt32(u)
}

@UsedFromCompilerGeneratedCode
internal fun bitUshr(
    a: Int,
    bitCount: Int,
): Int {
    val s: dynamic = luaMod(bitCount, 32)
    val q: dynamic = luaDiv(luaMod(a, 4294967296.0), luaPow(2, s))
    val u: dynamic = LuaMath.floor(q)
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

@UsedFromCompilerGeneratedCode
internal class LuaInternalError(
    val rawError: Any?,
) : Error(anyToString(rawError))

@UsedFromCompilerGeneratedCode
internal fun luaMainArgs(): Array<String> {
    val tbl: dynamic =
        lua(
            """
            (function()
                if type(arg) == 'table' then
                    local t = {}
                    local n = #arg
                    for i = 1, n do
                        t[i] = tostring(arg[i])
                    end
                    return t
                end
                return {}
            end)()
            """,
        )
    val count: Int = lua("#tbl")
    return Array(tbl, count, true)
}

@UsedFromCompilerGeneratedCode
internal fun luaUnpack(arr: Array<*>): dynamic {
    val storage: dynamic = arr.storage
    val size: Int = arr.size
    return lua("unpack(storage, 1, size)")
}

@UsedFromCompilerGeneratedCode
internal fun stringGet(s: String, index: Int): Char {
    val idx1 = luaPlus(index, 1).unsafeCast<Int>()
    return LuaString.sub(s, idx1, idx1).unsafeCast<Char>()
}

@UsedFromCompilerGeneratedCode
internal fun stringSubSequence(s: String, startIndex: Int, endIndex: Int): CharSequence {
    AbstractList.checkBoundsIndexes(startIndex, endIndex, luaLength(s))
    if (luaEq(startIndex, endIndex)) {
        return ""
    }
    return LuaString.sub(s, luaPlus(startIndex, 1).unsafeCast<Int>(), endIndex)
}

@UsedFromCompilerGeneratedCode
internal fun numberToInt(v: Any?): Int {
    if (luaNeq(v, v)) {
        return 0
    }
    if (luaGte(v, 2147483647.0)) {
        return 2147483647
    }
    if (luaLte(v, -2147483648.0)) {
        return -2147483648
    }
    return if (luaGte(v, 0)) {
        LuaMath.floor(v)
    } else {
        LuaMath.ceil(v)
    }
}

@UsedFromCompilerGeneratedCode
internal fun numberToByte(v: Any?): Byte = intToByte(numberToInt(v))

@UsedFromCompilerGeneratedCode
internal fun numberToShort(v: Any?): Short = intToShort(numberToInt(v))

@UsedFromCompilerGeneratedCode
internal fun charToInt(c: Char): Int {
    val len = luaLength(c)
    val s = c.unsafeCast<String>()
    val b0 = LuaString.byte(s, 1)
    if (luaEq(len, 1)) {
        return b0
    }
    if (luaEq(len, 2)) {
        val b1 = LuaString.byte(s, 2)
        return bitOr(bitShl(bitAnd(b0, 0x1F), 6), bitAnd(b1, 0x3F))
    }
    if (luaEq(len, 3)) {
        val b1 = LuaString.byte(s, 2)
        val b2 = LuaString.byte(s, 3)
        return bitOr(
            bitShl(bitAnd(b0, 0x0F), 12),
            bitOr(bitShl(bitAnd(b1, 0x3F), 6), bitAnd(b2, 0x3F)),
        )
    }
    return b0
}

@UsedFromCompilerGeneratedCode
internal fun charToByte(c: Char): Byte = intToByte(charToInt(c))

@UsedFromCompilerGeneratedCode
internal fun charToShort(c: Char): Short = intToShort(charToInt(c))

@UsedFromCompilerGeneratedCode
internal fun intToChar(code: Int): Char {
    val c = bitAnd(code, 0xFFFF)
    if (luaLt(c, 0x80)) {
        return LuaString.char(c).unsafeCast<Char>()
    }
    if (luaLt(c, 0x800)) {
        val b0 = bitOr(0xC0, bitShr(c, 6))
        val b1 = bitOr(0x80, bitAnd(c, 0x3F))
        return luaConcat(LuaString.char(b0), LuaString.char(b1)).unsafeCast<Char>()
    }
    val b0 = bitOr(0xE0, bitShr(c, 12))
    val b1 = bitOr(0x80, bitAnd(bitShr(c, 6), 0x3F))
    val b2 = bitOr(0x80, bitAnd(c, 0x3F))
    return luaConcat(luaConcat(LuaString.char(b0), LuaString.char(b1)), LuaString.char(b2)).unsafeCast<Char>()
}

@UsedFromCompilerGeneratedCode
internal fun numberToChar(v: Any?): Char = intToChar(numberToInt(v))

@UsedFromCompilerGeneratedCode
internal fun charPlusInt(c: Char, other: Int): Char =
    intToChar(luaPlus(charToInt(c), other).unsafeCast<Int>())

@UsedFromCompilerGeneratedCode
internal fun charMinusChar(a: Char, b: Char): Int =
    luaMinus(charToInt(a), charToInt(b)).unsafeCast<Int>()

@UsedFromCompilerGeneratedCode
internal fun charMinusInt(c: Char, other: Int): Char =
    intToChar(luaMinus(charToInt(c), other).unsafeCast<Int>())

@UsedFromCompilerGeneratedCode
internal fun intToByte(v: Int): Byte {
    val b: Any? = luaMod(v, 256)
    return if (luaGte(b, 128)) luaMinus(b, 256).unsafeCast<Byte>() else b.unsafeCast<Byte>()
}

@UsedFromCompilerGeneratedCode
internal fun intToShort(v: Int): Short {
    val s: Any? = luaMod(v, 65536)
    return if (luaGte(s, 32768)) luaMinus(s, 65536).unsafeCast<Short>() else s.unsafeCast<Short>()
}

@DoNotIntrinsify
@UsedFromCompilerGeneratedCode
internal fun charSequenceLength(csq: CharSequence): Int {
    if (isTypeOf(csq, "string")) {
        return luaLength(csq)
    }
    return csq.length
}

@DoNotIntrinsify
@UsedFromCompilerGeneratedCode
internal fun charSequenceGet(csq: CharSequence, index: Int): Char {
    if (isTypeOf(csq, "string")) {
        return stringGet(csq.unsafeCast<String>(), index)
    }
    return csq[index]
}

@DoNotIntrinsify
@UsedFromCompilerGeneratedCode
internal fun charSequenceSubSequence(csq: CharSequence, startIndex: Int, endIndex: Int): CharSequence {
    if (isTypeOf(csq, "string")) {
        return stringSubSequence(csq.unsafeCast<String>(), startIndex, endIndex)
    }
    return csq.subSequence(startIndex, endIndex)
}

@UsedFromCompilerGeneratedCode
internal fun numberRangeToNumber(start: Int, endInclusive: Int): IntRange =
    IntRange(start, endInclusive)

@UsedFromCompilerGeneratedCode
internal fun charRangeToChar(start: Char, endInclusive: Char): CharRange =
    CharRange(start, endInclusive)

@UsedFromCompilerGeneratedCode
internal fun boolAnd(a: Boolean, b: Boolean): Boolean = luaAnd(a, b).unsafeCast<Boolean>()

@UsedFromCompilerGeneratedCode
internal fun boolOr(a: Boolean, b: Boolean): Boolean = luaOr(a, b).unsafeCast<Boolean>()

@UsedFromCompilerGeneratedCode
internal fun boolXor(a: Boolean, b: Boolean): Boolean = luaNeq(a, b)
