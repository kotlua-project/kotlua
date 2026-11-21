@file:OptIn(LuaIntrinsic::class)

package kotlin.collections

import kotlin.lua.LuaIntrinsic
import kotlin.lua.luaEmptyTable
import kotlin.lua.luaLte
import kotlin.lua.luaTableGet
import kotlin.lua.luaTableSet

private inline fun mergeSortStorage(
    storage: dynamic,
    size: Int,
    fromIndex: Int,
    toIndex: Int,
    lte: (Any?, Any?) -> Boolean,
) {
    if (fromIndex < 0 || toIndex > size) {
        throw IndexOutOfBoundsException("fromIndex: $fromIndex, toIndex: $toIndex, size: $size")
    }
    if (fromIndex > toIndex) {
        throw IllegalArgumentException("fromIndex: $fromIndex > toIndex: $toIndex")
    }
    val len = toIndex - fromIndex
    if (len <= 1) return

    val from1 = fromIndex + 1
    val to1 = toIndex + 1
    val tmp: dynamic = luaEmptyTable()

    var width = 1
    while (width < len) {
        var start = from1
        while (start < to1) {
            val mid = if (start + width < to1) start + width else to1
            val right = if (start + width * 2 < to1) start + width * 2 else to1
            if (mid < right) {
                var i = start
                var j = mid
                var k = 1
                while (i < mid && j < right) {
                    val vi = luaTableGet(storage, i)
                    val vj = luaTableGet(storage, j)
                    if (lte(vi, vj)) {
                        luaTableSet(tmp, k, vi)
                        i++
                    } else {
                        luaTableSet(tmp, k, vj)
                        j++
                    }
                    k++
                }
                while (i < mid) {
                    luaTableSet(tmp, k, luaTableGet(storage, i))
                    i++
                    k++
                }
                while (j < right) {
                    luaTableSet(tmp, k, luaTableGet(storage, j))
                    j++
                    k++
                }
                var idx = 1
                while (idx < k) {
                    luaTableSet(storage, start + idx - 1, luaTableGet(tmp, idx))
                    idx++
                }
            }
            start += width * 2
        }
        width *= 2
    }
}

internal fun sortArrayPrimitive(
    storage: dynamic,
    size: Int,
    fromIndex: Int,
    toIndex: Int,
) {
    mergeSortStorage(storage, size, fromIndex, toIndex) { a, b ->
        luaLte(a, b)
    }
}

internal fun sortArrayComparable(
    storage: dynamic,
    size: Int,
    fromIndex: Int,
    toIndex: Int,
) {
    mergeSortStorage(storage, size, fromIndex, toIndex) { a, b ->
        @Suppress("UNCHECKED_CAST")
        (a as Comparable<Any?>).compareTo(b) <= 0
    }
}

internal fun <T> sortArrayWith(
    storage: dynamic,
    size: Int,
    fromIndex: Int,
    toIndex: Int,
    comparator: Comparator<in T>,
) {
    mergeSortStorage(storage, size, fromIndex, toIndex) { a, b ->
        @Suppress("UNCHECKED_CAST")
        comparator.compare(a as T, b as T) <= 0
    }
}
