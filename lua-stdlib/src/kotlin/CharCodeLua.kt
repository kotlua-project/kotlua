package kotlin

/**
 * Creates a Char with the specified [code].
 *
 * @sample samples.text.Chars.charFromCode
 */
@SinceKotlin("1.5")
@kotlin.internal.InlineOnly
@Suppress("DEPRECATION")
public actual inline fun Char(code: UShort): Char = code.toInt().toChar()
