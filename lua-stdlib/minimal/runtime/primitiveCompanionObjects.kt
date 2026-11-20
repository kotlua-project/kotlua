package kotlin.lua.internal

import kotlin.internal.UsedFromCompilerGeneratedCode

@UsedFromCompilerGeneratedCode
internal object DoubleCompanionObject {
    const val MIN_VALUE: Double = 4.9E-324
    const val MAX_VALUE: Double = 1.7976931348623157E308

    @Suppress("DIVISION_BY_ZERO")
    const val POSITIVE_INFINITY: Double = 1.0 / 0.0

    @Suppress("DIVISION_BY_ZERO")
    const val NEGATIVE_INFINITY: Double = -1.0 / 0.0

    @Suppress("DIVISION_BY_ZERO")
    const val NaN: Double = -(0.0 / 0.0)

    const val SIZE_BYTES = 8
    const val SIZE_BITS = 64
}

@UsedFromCompilerGeneratedCode
internal object FloatCompanionObject {
    const val MIN_VALUE: Float = 1.4E-45F
    const val MAX_VALUE: Float = 3.4028235E38F

    @Suppress("DIVISION_BY_ZERO")
    const val POSITIVE_INFINITY: Float = 1.0F / 0.0F

    @Suppress("DIVISION_BY_ZERO")
    const val NEGATIVE_INFINITY: Float = -1.0F / 0.0F

    @Suppress("DIVISION_BY_ZERO")
    const val NaN: Float = -(0.0F / 0.0F)

    const val SIZE_BYTES = 4
    const val SIZE_BITS = 32
}

@UsedFromCompilerGeneratedCode
internal object IntCompanionObject {
    const val MIN_VALUE: Int = -2147483647 - 1
    const val MAX_VALUE: Int = 2147483647
    const val SIZE_BYTES = 4
    const val SIZE_BITS = 32
}

@UsedFromCompilerGeneratedCode
internal object LongCompanionObject {
    const val MIN_VALUE: Long = -9223372036854775807L - 1L
    const val MAX_VALUE: Long = 9223372036854775807L
    const val SIZE_BYTES = 8
    const val SIZE_BITS = 64
}

@UsedFromCompilerGeneratedCode
internal object ShortCompanionObject {
    const val MIN_VALUE: Short = -32768
    const val MAX_VALUE: Short = 32767
    const val SIZE_BYTES = 2
    const val SIZE_BITS = 16
}

@UsedFromCompilerGeneratedCode
internal object ByteCompanionObject {
    const val MIN_VALUE: Byte = -128
    const val MAX_VALUE: Byte = 127
    const val SIZE_BYTES = 1
    const val SIZE_BITS = 8
}

@UsedFromCompilerGeneratedCode
internal object CharCompanionObject {
    const val MIN_VALUE: Char = '\u0000'
    const val MAX_VALUE: Char = '\uFFFF'
    const val MIN_HIGH_SURROGATE: Char = '\uD800'
    const val MAX_HIGH_SURROGATE: Char = '\uDBFF'
    const val MIN_LOW_SURROGATE: Char = '\uDC00'
    const val MAX_LOW_SURROGATE: Char = '\uDFFF'
    const val MIN_SURROGATE: Char = MIN_HIGH_SURROGATE
    const val MAX_SURROGATE: Char = MAX_LOW_SURROGATE
    const val SIZE_BYTES = 2
    const val SIZE_BITS = 16
}

@UsedFromCompilerGeneratedCode
internal object StringCompanionObject

@UsedFromCompilerGeneratedCode
internal object BooleanCompanionObject
