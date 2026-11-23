@file:OptIn(LuaIntrinsic::class, ExperimentalTime::class)

package kotlin.time

import kotlin.lua.LuaIntrinsic
import kotlin.lua.LuaMath
import kotlin.lua.luaDiv
import kotlin.lua.luaMult

@SinceKotlin("1.6")
@WasExperimental(ExperimentalTime::class)
public actual enum class DurationUnit(
    internal val nanosScale: Double,
) {
    NANOSECONDS(1.0),
    MICROSECONDS(1_000.0),
    MILLISECONDS(1_000_000.0),
    SECONDS(1_000_000_000.0),
    MINUTES(60_000_000_000.0),
    HOURS(3_600_000_000_000.0),
    DAYS(86_400_000_000_000.0),
}

@SinceKotlin("1.3")
internal actual fun convertDurationUnit(
    value: Double,
    sourceUnit: DurationUnit,
    targetUnit: DurationUnit,
): Double {
    if (sourceUnit == targetUnit) return value
    val sourceInTarget = sourceUnit.nanosScale / targetUnit.nanosScale
    return if (sourceUnit > targetUnit) {
        value * sourceInTarget
    } else {
        value / (targetUnit.nanosScale / sourceUnit.nanosScale)
    }
}

@SinceKotlin("1.5")
internal actual fun convertDurationUnitOverflow(
    value: Long,
    sourceUnit: DurationUnit,
    targetUnit: DurationUnit,
): Long {
    if (sourceUnit <= targetUnit) {
        return convertDurationUnit(value, sourceUnit, targetUnit)
    }
    val factor = sourceUnit.nanosScale / targetUnit.nanosScale
    val maxBeforeOverflow = LuaMath.floor(luaDiv(Long.MAX_VALUE.toDouble(), factor)).toDouble()
    val vDouble = value.toDouble()
    if (vDouble > maxBeforeOverflow) return Long.MAX_VALUE
    if (vDouble < -maxBeforeOverflow) return Long.MIN_VALUE
    return luaMult(value, factor).unsafeCast<Long>()
}

@SinceKotlin("1.5")
internal actual fun convertDurationUnit(
    value: Long,
    sourceUnit: DurationUnit,
    targetUnit: DurationUnit,
): Long {
    if (sourceUnit == targetUnit) return value
    return if (sourceUnit > targetUnit) {
        val factor = sourceUnit.nanosScale / targetUnit.nanosScale
        luaMult(value, factor).unsafeCast<Long>()
    } else {
        val factor = targetUnit.nanosScale / sourceUnit.nanosScale
        val q = luaDiv(value, factor)
        val truncated = if (value >= 0L) LuaMath.floor(q) else LuaMath.ceil(q)
        truncated.toLong()
    }
}
