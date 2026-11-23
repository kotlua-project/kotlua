@file:OptIn(LuaIntrinsic::class)

package kotlin.collections

import kotlin.lua.LuaGlobal
import kotlin.lua.LuaIntrinsic
import kotlin.lua.LuaName
import kotlin.lua.luaEmptyTable
import kotlin.lua.luaEq
import kotlin.lua.luaTableGet
import kotlin.lua.luaTableSet

@LuaGlobal
@LuaName("table")
private external object LuaTable {
    fun concat(list: dynamic): String
}

internal fun <T> Array<out T>?.contentDeepHashCodeImpl(): Int {
    if (this === null) return 0
    var result = 1
    var i = 0
    val n = this.size
    while (i < n) {
        val elementHash = when (val element = this[i]) {
            null -> 0
            is Array<*> -> element.contentDeepHashCodeImpl()
            is ByteArray -> element.contentHashCode()
            is ShortArray -> element.contentHashCode()
            is IntArray -> element.contentHashCode()
            is LongArray -> element.contentHashCode()
            is FloatArray -> element.contentHashCode()
            is DoubleArray -> element.contentHashCode()
            is CharArray -> element.contentHashCode()
            is BooleanArray -> element.contentHashCode()
            else -> element.hashCode()
        }
        result = 31 * result + elementHash
        i++
    }
    return result
}

internal fun <T> Array<out T>?.luaContentDeepToStringImpl(): String {
    if (this === null) return "null"
    val parts: dynamic = luaEmptyTable()
    val processed: dynamic = luaEmptyTable()
    appendDeepToString(this, parts, 0, processed)
    return LuaTable.concat(parts)
}

private fun appendDeepToString(
    array: Array<*>,
    parts: dynamic,
    partCount: Int,
    processed: dynamic,
): Int {
    if (luaEq(luaTableGet(processed, array), true)) {
        val next = partCount + 1
        luaTableSet(parts, next, "[...]")
        return next
    }
    luaTableSet(processed, array, true)

    var count = partCount + 1
    luaTableSet(parts, count, "[")

    var i = 0
    val n = array.size
    while (i < n) {
        if (i > 0) {
            count++
            luaTableSet(parts, count, ", ")
        }
        when (val element = array[i]) {
            null -> {
                count++
                luaTableSet(parts, count, "null")
            }
            is Array<*> -> {
                count = appendDeepToString(element, parts, count, processed)
            }
            is ByteArray -> count = appendPrimitiveArray(parts, count, element.size) { element[it].toString() }
            is ShortArray -> count = appendPrimitiveArray(parts, count, element.size) { element[it].toString() }
            is IntArray -> count = appendPrimitiveArray(parts, count, element.size) { element[it].toString() }
            is LongArray -> count = appendPrimitiveArray(parts, count, element.size) { element[it].toString() }
            is FloatArray -> count = appendPrimitiveArray(parts, count, element.size) { element[it].toString() }
            is DoubleArray -> count = appendPrimitiveArray(parts, count, element.size) { element[it].toString() }
            is CharArray -> count = appendPrimitiveArray(parts, count, element.size) { element[it].toString() }
            is BooleanArray -> count = appendPrimitiveArray(parts, count, element.size) { element[it].toString() }
            else -> {
                count++
                luaTableSet(parts, count, element.toString())
            }
        }
        i++
    }

    count++
    luaTableSet(parts, count, "]")
    luaTableSet(processed, array, null)
    return count
}

private inline fun appendPrimitiveArray(
    parts: dynamic,
    partCount: Int,
    size: Int,
    elementToString: (Int) -> String,
): Int {
    var count = partCount + 1
    luaTableSet(parts, count, "[")
    var i = 0
    while (i < size) {
        if (i > 0) {
            count++
            luaTableSet(parts, count, ", ")
        }
        count++
        luaTableSet(parts, count, elementToString(i))
        i++
    }
    count++
    luaTableSet(parts, count, "]")
    return count
}
