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
