package kotlin.random

import kotlin.lua.LuaGlobal
import kotlin.lua.LuaMath
import kotlin.lua.LuaName
import kotlin.lua.lua

@LuaGlobal
@LuaName("math")
private external object LuaRandomMath {
    fun random(): Double
}

private fun nextPlatformSeed(): Int {
    val hi = LuaMath.floor(LuaRandomMath.random() * 65536.0)
    val lo = LuaMath.floor(LuaRandomMath.random() * 65536.0)
    val timeSeed: Double = lua("type(os) == 'table' and type(os.time) == 'function' and os.time() or 0")
    val u = (hi * 65536.0 + lo + timeSeed * 131071.0) % 4294967296.0
    val signed = if (u >= 2147483648.0) u - 4294967296.0 else u
    return signed.unsafeCast<Int>()
}

private val defaultRandom: Random = Random(nextPlatformSeed())

internal actual fun defaultPlatformRandom(): Random = defaultRandom

private val INV_2_26: Double = 1.0 / 67108864.0
private val INV_2_53: Double = 1.0 / 9007199254740992.0

internal actual fun doubleFromParts(hi26: Int, low27: Int): Double =
    hi26 * INV_2_26 + low27 * INV_2_53
