/*
 * Copyright 2026 The kotlua Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.kotlua

import kotlin.text.iterator

object LuaStringEscaper {
    fun escape(
        chars: CharSequence,
        forceDoubleQuote: Boolean = false,
    ): String {
        var quoteCount = 0
        var aposCount = 0

        for (c in chars) {
            when (c) {
                '"' -> quoteCount++
                '\'' -> aposCount++
            }
        }

        val quoteChar = if (quoteCount < aposCount || forceDoubleQuote) '"' else '\''
        val result = StringBuilder(chars.length + 16)

        result.append(quoteChar)

        for (c in chars) {
            with(result) {
                if (c == quoteChar) {
                    append('\\').append(quoteChar)
                    continue
                }

                when (c) {
                    '\\' -> {
                        append("\\\\")
                    }

                    '\n' -> {
                        append("\\n")
                    }

                    '\r' -> {
                        append("\\r")
                    }

                    '\t' -> {
                        append("\\t")
                    }

                    '\b' -> {
                        append("\\b")
                    }

                    '\u000c' -> {
                        append("\\f")
                    }

                    '\u0007' -> {
                        append("\\a")
                    }

                    '\u000b' -> {
                        append("\\v")
                    }

                    else -> {
                        val code = c.code
                        if (code < 32 || code == 127) {
                            append(String.format("\\%03d", code))
                        } else {
                            append(c)
                        }
                    }
                }
            }
        }

        result.append(quoteChar)
        return result.toString()
    }
}
