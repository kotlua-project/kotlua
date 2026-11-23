package kotlin.time

import kotlin.lua.lua
import kotlin.math.floor
import kotlin.math.round

internal actual fun systemClockNow(): Instant {
    val epochSeconds: Double = lua("type(os) == 'table' and type(os.time) == 'function' and os.time() or 0")
    return Instant.fromEpochSeconds(epochSeconds.toLong(), 0)
}

internal actual fun serializedInstant(instant: Instant): Any = instant

internal actual val durationAssertionsEnabled: Boolean = false

internal actual fun formatToExactDecimals(value: Double, decimals: Int): String {
    if (!value.isFinite()) return value.toString()
    val isNeg = value < 0.0
    val absVal = if (isNeg) -value else value
    var scale = 1.0
    for (i in 0 until decimals) {
        scale *= 10.0
    }
    val rounded = round(absVal * scale)
    val intPart = floor(rounded / scale)
    val fracPart = rounded - intPart * scale
    val sb = StringBuilder()
    if (isNeg && rounded > 0.0) sb.append('-')
    if (intPart < 9007199254740992.0) {
        sb.append(intPart.toLong().toString())
    } else {
        sb.append(intPart.toString())
    }
    if (decimals > 0) {
        sb.append('.')
        val fracStr = fracPart.toLong().toString()
        for (i in fracStr.length until decimals) {
            sb.append('0')
        }
        sb.append(fracStr)
    }
    return sb.toString()
}

internal actual typealias ValueTimeMarkReading = Double

@SinceKotlin("1.3")
internal actual object MonotonicTimeSource : TimeSource.WithComparableMarks {
    private fun readSeconds(): Double =
        lua("type(os) == 'table' and type(os.clock) == 'function' and os.clock() or 0")

    actual override fun markNow(): TimeSource.Monotonic.ValueTimeMark =
        TimeSource.Monotonic.ValueTimeMark(readSeconds())

    actual fun elapsedFrom(timeMark: TimeSource.Monotonic.ValueTimeMark): Duration =
        (readSeconds() - timeMark.reading).toDuration(DurationUnit.SECONDS)

    actual fun differenceBetween(
        one: TimeSource.Monotonic.ValueTimeMark,
        another: TimeSource.Monotonic.ValueTimeMark,
    ): Duration =
        if (one.reading == another.reading) {
            Duration.ZERO
        } else {
            (one.reading - another.reading).toDuration(DurationUnit.SECONDS)
        }

    actual fun adjustReading(
        timeMark: TimeSource.Monotonic.ValueTimeMark,
        duration: Duration,
    ): TimeSource.Monotonic.ValueTimeMark =
        TimeSource.Monotonic.ValueTimeMark(timeMark.reading + duration.toDouble(DurationUnit.SECONDS))

    override fun toString(): String = "TimeSource(os.clock())"
}
