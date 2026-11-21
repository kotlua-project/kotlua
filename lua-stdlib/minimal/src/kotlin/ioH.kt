/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.io

/** Prints the line separator to the standard output stream. */
public actual fun println() {
    luaPrintln(null)
}

/** Prints the given [message] and the line separator to the standard output stream. */
public actual fun println(message: Any?) {
    luaPrintln(message)
}

/** Prints the given [message] to the standard output stream. */
public actual fun print(message: Any?) {
    LuaIO.write(message)
}

/**
 * Reads a line of input from the standard input stream and returns it,
 * or throws a [RuntimeException] if EOF has already been reached when [readln] is called.
 *
 * LF or CRLF is treated as the line terminator. Line terminator is not included in the returned string.
 *
 * Currently this function is not supported in Kotlin/JS and in Kotlin/WasmJS, and throws [UnsupportedOperationException].
 */
public actual fun readln(): String {
    return readlnOrNull()!!
}

/**
 * Reads a line of input from the standard input stream and returns it,
 * or return `null` if EOF has already been reached when [readlnOrNull] is called.
 *
 * LF or CRLF is treated as the line terminator. Line terminator is not included in the returned string.
 *
 * Currently this function is not supported in Kotlin/JS and in Kotlin/WasmJS, and throws [UnsupportedOperationException].
 */
public actual fun readlnOrNull(): String? {
    return LuaIO.read()
}

@LuaGlobal
@LuaName("print")
internal external fun luaPrintln(message: Any?)

@LuaGlobal
@LuaName("io")
internal external object LuaIO {
    fun read(): String?

    fun write(message: Any?)
}

internal actual interface Serializable
