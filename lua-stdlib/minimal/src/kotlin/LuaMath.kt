package kotlin.lua

@LuaGlobal
@LuaName("math")
external object LuaMath {
    fun floor(v: Any?): Int
    fun ceil(v: Any?): Int
    fun fmod(a: Any?, b: Any?): Float
}
