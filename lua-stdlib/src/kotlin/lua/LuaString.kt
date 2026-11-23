package kotlin.lua

/**
 * External bindings for the standard Lua 5.1 `string` library.
 */
@LuaGlobal
@LuaName("string")
external object LuaString {
    /**
     * Returns the byte length of string [s].
     *
     * @param s the input string.
     * @return the length of [s] in bytes.
     */
    fun len(s: String): Int

    /**
     * Returns the substring of [s] starting at 1-based index [i] through the end of the string.
     *
     * @param s the input string.
     * @param i the 1-based start index (negative values count from the end).
     * @return the resulting substring.
     */
    fun sub(s: String, i: Int): String

    /**
     * Returns the substring of [s] from 1-based index [i] to [j] inclusive.
     *
     * @param s the input string.
     * @param i the 1-based start index (negative values count from the end).
     * @param j the 1-based end index (inclusive; negative values count from the end).
     * @return the resulting substring.
     */
    fun sub(s: String, i: Int, j: Int): String

    /**
     * Returns a string consisting of [n] copies of [s] concatenated together.
     *
     * @param s the string to repeat.
     * @param n the number of repetitions.
     * @return the repeated string, or `""` if `n <= 0`.
     */
    fun rep(s: String, n: Int): String

    /**
     * Returns a string that is the byte-wise reverse of [s].
     *
     * @param s the input string.
     * @return the reversed string.
     */
    fun reverse(s: String): String

    /**
     * Returns a copy of [s] with all ASCII uppercase letters converted to lowercase (locale-dependent in Lua).
     *
     * @param s the input string.
     * @return the lowercased string.
     */
    fun lower(s: String): String

    /**
     * Returns a copy of [s] with all ASCII lowercase letters converted to uppercase (locale-dependent in Lua).
     *
     * @param s the input string.
     * @return the uppercased string.
     */
    fun upper(s: String): String

    /**
     * Returns the internal numeric byte code of the first character of [s].
     *
     * @param s the input string.
     * @return the unsigned byte value (`0..255`) at index `1`.
     */
    fun byte(s: String): Int

    /**
     * Returns the internal numeric byte code of the character at 1-based index [i] in [s].
     *
     * @param s the input string.
     * @param i the 1-based byte index.
     * @return the unsigned byte value (`0..255`) at index [i].
     */
    fun byte(s: String, i: Int): Int

    /**
     * Returns a single-byte string corresponding to numeric code [b].
     *
     * @param b the byte value in `0..255`.
     * @return a string of length `1` containing byte [b].
     */
    fun char(b: Int): String

    /**
     * Returns a string formed by bytes corresponding to each code in [bytes].
     *
     * @param bytes the byte values in `0..255`.
     * @return a string containing the specified bytes in order.
     */
    fun char(vararg bytes: Int): String

    /**
     * Finds the first match of Lua [pattern] in [s].
     *
     * @param s the input string.
     * @param pattern the Lua pattern to search for.
     * @return the 1-based start index of the match, or `null` if not found.
     */
    fun find(s: String, pattern: String): Int?

    /**
     * Finds the first match of Lua [pattern] in [s] starting at 1-based index [init].
     *
     * @param s the input string.
     * @param pattern the Lua pattern to search for.
     * @param init the 1-based start index for the search.
     * @return the 1-based start index of the match, or `null` if not found.
     */
    fun find(s: String, pattern: String, init: Int): Int?

    /**
     * Finds the first occurrence of [pattern] in [s] starting at 1-based index [init].
     *
     * @param s the input string.
     * @param pattern the Lua pattern or plain substring to search for.
     * @param init the 1-based start index for the search.
     * @param plain if `true`, disables pattern matching and performs a plain substring search.
     * @return the 1-based start index of the match, or `null` if not found.
     */
    fun find(s: String, pattern: String, init: Int, plain: Boolean): Int?

    /**
     * Searches for the first match of Lua [pattern] in [s].
     *
     * @param s the input string.
     * @param pattern the Lua pattern to match.
     * @return the first capture (or the whole matched substring if there are no captures), or `null`.
     */
    fun match(s: String, pattern: String): String?

    /**
     * Searches for the first match of Lua [pattern] in [s] starting at 1-based index [init].
     *
     * @param s the input string.
     * @param pattern the Lua pattern to match.
     * @param init the 1-based start index for the search.
     * @return the first capture (or the whole matched substring if there are no captures), or `null`.
     */
    fun match(s: String, pattern: String, init: Int): String?

    /**
     * Replaces all occurrences of Lua [pattern] in [s] with replacement template [repl].
     *
     * @param s the input string.
     * @param pattern the Lua pattern to replace.
     * @param repl the replacement string (may reference captures via `%1`..`%9`).
     * @return the resulting string after substitution.
     */
    fun gsub(s: String, pattern: String, repl: String): String

    /**
     * Replaces up to [n] occurrences of Lua [pattern] in [s] with replacement template [repl].
     *
     * @param s the input string.
     * @param pattern the Lua pattern to replace.
     * @param repl the replacement string (may reference captures via `%1`..`%9`).
     * @param n the maximum number of substitutions to perform.
     * @return the resulting string after substitution.
     */
    fun gsub(s: String, pattern: String, repl: String, n: Int): String

    /**
     * Formats [args] according to C `printf`-style [formatString] (Lua `string.format` rules).
     *
     * @param formatString the format specification string.
     * @param args the values to format.
     * @return the formatted string.
     */
    fun format(formatString: String, vararg args: Any?): String
}
