package kotlin.lua

/**
 * External bindings for the standard Lua 5.1 `table` library.
 */
@LuaGlobal
@LuaName("table")
external object LuaTable {
    /**
     * Concatenates all string/number elements of [list] into a single string.
     *
     * @param list a 1-based sequence table whose elements are strings or numbers.
     * @return the concatenated string.
     */
    fun concat(list: dynamic): String

    /**
     * Concatenates elements of [list] separated by [sep].
     *
     * @param list a 1-based sequence table whose elements are strings or numbers.
     * @param sep the separator inserted between consecutive elements.
     * @return the concatenated string.
     */
    fun concat(list: dynamic, sep: String): String

    /**
     * Concatenates elements of [list] starting from 1-based index [i], separated by [sep].
     *
     * @param list a 1-based sequence table whose elements are strings or numbers.
     * @param sep the separator inserted between consecutive elements.
     * @param i the 1-based start index.
     * @return the concatenated string.
     */
    fun concat(list: dynamic, sep: String, i: Int): String

    /**
     * Concatenates elements of [list] from 1-based index [i] to [j] inclusive, separated by [sep].
     *
     * @param list a 1-based sequence table whose elements are strings or numbers.
     * @param sep the separator inserted between consecutive elements.
     * @param i the 1-based start index.
     * @param j the 1-based end index (inclusive).
     * @return the concatenated string, or `""` if `i > j`.
     */
    fun concat(list: dynamic, sep: String, i: Int, j: Int): String

    /**
     * Inserts [value] at the end of [list] (`#list + 1`).
     *
     * @param list the target Lua sequence table.
     * @param value the value to append.
     */
    fun insert(list: dynamic, value: Any?)

    /**
     * Inserts [value] at 1-based position [pos] in [list], shifting subsequent elements up.
     *
     * @param list the target Lua sequence table.
     * @param pos the 1-based insertion index.
     * @param value the value to insert.
     */
    fun insert(list: dynamic, pos: Int, value: Any?)

    /**
     * Removes and returns the last element of [list].
     *
     * @param list the target Lua sequence table.
     * @return the removed element, or `null` if the table is empty.
     */
    fun remove(list: dynamic): Any?

    /**
     * Removes and returns the element at 1-based position [pos] in [list], shifting subsequent elements down.
     *
     * @param list the target Lua sequence table.
     * @param pos the 1-based index of the element to remove.
     * @return the removed element, or `null` if [pos] is out of range.
     */
    fun remove(list: dynamic, pos: Int): Any?

    /**
     * Returns the largest positive numerical key in [table], or `0` if none exists.
     *
     * @param table the Lua table to inspect.
     * @return the largest positive numeric index, or `0`.
     */
    fun maxn(table: dynamic): Int

    /**
     * Sorts elements of [list] in-place in ascending order using Lua's `<` operator.
     *
     * @param list the 1-based Lua sequence table to sort.
     */
    fun sort(list: dynamic)

    /**
     * Sorts elements of [list] in-place using the given comparator predicate [comp].
     *
     * @param list the 1-based Lua sequence table to sort.
     * @param comp a comparator returning `true` when its first argument must come before the second.
     */
    fun sort(list: dynamic, comp: (Any?, Any?) -> Boolean)
}
