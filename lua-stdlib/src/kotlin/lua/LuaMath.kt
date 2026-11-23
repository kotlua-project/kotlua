package kotlin.lua

/**
 * External bindings for the standard Lua 5.1 `math` library.
 */
@LuaGlobal
@LuaName("math")
external object LuaMath {
    /**
     * The value of π (`math.pi`).
     */
    val pi: Double

    /**
     * Positive infinity (`math.huge`), a value larger than or equal to any other numerical value.
     */
    val huge: Double

    /**
     * Returns the absolute value of [x].
     *
     * @param x the numeric value.
     * @return the non-negative absolute value of [x].
     */
    fun abs(x: Double): Double

    /**
     * Returns the absolute value of integer [x].
     *
     * @param x the integer value.
     * @return the non-negative absolute value of [x].
     */
    fun abs(x: Int): Int

    /**
     * Returns the arc cosine of [x] in radians.
     *
     * @param x the value in `[-1.0, 1.0]`.
     * @return the arc cosine of [x] in `[0, π]`.
     */
    fun acos(x: Double): Double

    /**
     * Returns the arc sine of [x] in radians.
     *
     * @param x the value in `[-1.0, 1.0]`.
     * @return the arc sine of [x] in `[-π/2, π/2]`.
     */
    fun asin(x: Double): Double

    /**
     * Returns the arc tangent of [x] in radians.
     *
     * @param x the input value.
     * @return the arc tangent of [x] in `[-π/2, π/2]`.
     */
    fun atan(x: Double): Double

    /**
     * Returns the arc tangent of `y / x` in radians, using the signs of both arguments to determine the quadrant.
     *
     * @param y the y-coordinate.
     * @param x the x-coordinate.
     * @return the angle in `[-π, π]`.
     */
    fun atan2(y: Double, x: Double): Double

    /**
     * Returns the cosine of [x] (given in radians).
     *
     * @param x the angle in radians.
     * @return the cosine of [x].
     */
    fun cos(x: Double): Double

    /**
     * Returns the hyperbolic cosine of [x].
     *
     * @param x the input value.
     * @return the hyperbolic cosine of [x].
     */
    fun cosh(x: Double): Double

    /**
     * Converts angle [x] from radians to degrees.
     *
     * @param x the angle in radians.
     * @return the equivalent angle in degrees.
     */
    fun deg(x: Double): Double

    /**
     * Returns `e^x`.
     *
     * @param x the exponent.
     * @return Euler's number `e` raised to the power [x].
     */
    fun exp(x: Double): Double

    /**
     * Returns the largest integer less than or equal to [v].
     *
     * @param v the numeric value.
     * @return the floored integer value.
     */
    fun floor(v: Any?): Int

    /**
     * Returns the smallest integer greater than or equal to [v].
     *
     * @param v the numeric value.
     * @return the ceiled integer value.
     */
    fun ceil(v: Any?): Int

    /**
     * Returns the remainder of the division of [a] by [b] that rounds the quotient towards zero.
     *
     * @param a the dividend.
     * @param b the divisor.
     * @return the floating-point remainder.
     */
    fun fmod(a: Any?, b: Any?): Float

    /**
     * Returns the natural logarithm (base `e`) of [x].
     *
     * @param x the positive input value.
     * @return `ln(x)`.
     */
    fun log(x: Double): Double

    /**
     * Returns the base-10 logarithm of [x].
     *
     * @param x the positive input value.
     * @return `log10(x)`.
     */
    fun log10(x: Double): Double

    /**
     * Returns the maximum value among [values].
     *
     * @param values one or more numbers to compare.
     * @return the largest value.
     */
    fun max(vararg values: Double): Double

    /**
     * Returns the minimum value among [values].
     *
     * @param values one or more numbers to compare.
     * @return the smallest value.
     */
    fun min(vararg values: Double): Double

    /**
     * Returns [x] raised to the power [y].
     *
     * @param x the base.
     * @param y the exponent.
     * @return `x^y`.
     */
    fun pow(x: Double, y: Double): Double

    /**
     * Converts angle [x] from degrees to radians.
     *
     * @param x the angle in degrees.
     * @return the equivalent angle in radians.
     */
    fun rad(x: Double): Double

    /**
     * Returns a pseudo-random uniform real number in `[0, 1)`.
     *
     * @return a pseudo-random `Double` in `[0, 1)`.
     */
    fun random(): Double

    /**
     * Returns a pseudo-random integer in `[1, m]`.
     *
     * @param m the inclusive upper bound (`>= 1`).
     * @return a pseudo-random integer in `1..m`.
     */
    fun random(m: Int): Int

    /**
     * Returns a pseudo-random integer in `[m, n]`.
     *
     * @param m the inclusive lower bound.
     * @param n the inclusive upper bound.
     * @return a pseudo-random integer in `m..n`.
     */
    fun random(m: Int, n: Int): Int

    /**
     * Sets [x] as the seed for the Lua pseudo-random generator.
     *
     * @param x the seed value.
     */
    fun randomseed(x: Int)

    /**
     * Returns the sine of [x] (given in radians).
     *
     * @param x the angle in radians.
     * @return the sine of [x].
     */
    fun sin(x: Double): Double

    /**
     * Returns the hyperbolic sine of [x].
     *
     * @param x the input value.
     * @return the hyperbolic sine of [x].
     */
    fun sinh(x: Double): Double

    /**
     * Returns the square root of [x].
     *
     * @param x the non-negative input value.
     * @return the square root of [x].
     */
    fun sqrt(x: Double): Double

    /**
     * Returns the tangent of [x] (given in radians).
     *
     * @param x the angle in radians.
     * @return the tangent of [x].
     */
    fun tan(x: Double): Double

    /**
     * Returns the hyperbolic tangent of [x].
     *
     * @param x the input value.
     * @return the hyperbolic tangent of [x].
     */
    fun tanh(x: Double): Double
}
