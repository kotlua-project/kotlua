@file:OptIn(LuaIntrinsic::class)

package kotlin

import kotlin.lua.LuaIntrinsic
import kotlin.lua.lua
import kotlin.lua.luaEq
import kotlin.lua.luaTableGet
import kotlin.lua.luaTableSet

private var suppressedExceptionsMap: dynamic = null

internal fun getSuppressedExceptionsListOrNull(throwable: Throwable): ArrayList<Throwable>? {
    if (luaEq(suppressedExceptionsMap, null)) return null
    @Suppress("UNCHECKED_CAST")
    return luaTableGet(suppressedExceptionsMap, throwable) as ArrayList<Throwable>?
}

/**
 * Returns the detailed description of this throwable with its stack trace.
 *
 * The detailed description includes:
 * - the short description (see [Throwable.toString]) of this throwable;
 * - the complete stack trace;
 * - detailed descriptions of the exceptions that were [suppressed][suppressedExceptions] in order to deliver this exception;
 * - the detailed description of each throwable in the [Throwable.cause] chain.
 */
@SinceKotlin("1.4")
public actual fun Throwable.stackTraceToString(): String {
    val sb = StringBuilder()
    appendStackTrace(this, sb, "")
    val tb: String? = lua("type(debug) == 'table' and type(debug.traceback) == 'function' and debug.traceback('', 2) or nil")
    if (tb != null && tb.isNotEmpty()) {
        sb.append('\n').append(tb)
    }
    return sb.toString()
}

private fun appendStackTrace(
    t: Throwable,
    sb: StringBuilder,
    indent: String,
) {
    sb.append(t.toString())
    for (suppressed in t.suppressedExceptions) {
        sb.append('\n').append(indent).append("\tSuppressed: ")
        appendStackTrace(suppressed, sb, indent + "\t")
    }
    val cause = t.cause
    if (cause != null && cause !== t) {
        sb.append('\n').append(indent).append("Caused by: ")
        appendStackTrace(cause, sb, indent)
    }
}

/**
 * Prints the [detailed description][Throwable.stackTraceToString] of this throwable to console error output.
 */
@SinceKotlin("1.4")
public actual fun Throwable.printStackTrace() {
    println(stackTraceToString())
}

/**
 * Adds the specified exception to the list of exceptions that were
 * suppressed in order to deliver this exception.
 */
@SinceKotlin("1.4")
public actual fun Throwable.addSuppressed(exception: Throwable) {
    if (this === exception) {
        throw IllegalArgumentException("Self-suppression not permitted", exception)
    }
    if (luaEq(suppressedExceptionsMap, null)) {
        suppressedExceptionsMap = lua("setmetatable({}, { __mode = 'k' })")
    }
    @Suppress("UNCHECKED_CAST")
    var list = luaTableGet(suppressedExceptionsMap, this) as ArrayList<Throwable>?
    if (list == null) {
        list = ArrayList()
        luaTableSet(suppressedExceptionsMap, this, list)
    }
    list.add(exception)
}
