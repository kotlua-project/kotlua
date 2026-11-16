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
package io.github.kotlua.ir.utils

private val LUA_KEYWORDS =
    setOf(
        "and",
        "break",
        "do",
        "else",
        "elseif",
        "end",
        "false",
        "for",
        "function",
        "if",
        "in",
        "local",
        "nil",
        "not",
        "or",
        "repeat",
        "return",
        "then",
        "true",
        "until",
        "while",
    )

fun makeValidLuaIdentifier(name: String): String {
    if (name.isEmpty()) return "_"

    val sanitized =
        buildString(name.length + 1) {
            for (i in name.indices) {
                val c = name[i]
                val valid = c in 'a'..'z' || c in 'A'..'Z' || c == '_' || (i > 0 && c in '0'..'9')
                if (i == 0 && c in '0'..'9') {
                    append('_')
                    append(c)
                } else {
                    append(if (valid) c else '_')
                }
            }
        }

    return if (sanitized in LUA_KEYWORDS) "${sanitized}_" else sanitized
}

abstract class NameScope {
    abstract fun isReserved(name: String): Boolean

    object EmptyScope : NameScope() {
        override fun isReserved(name: String): Boolean = false
    }
}

class NameTable<T>(
    private val parent: NameScope = EmptyScope,
) : NameScope() {
    private val reserved = hashSetOf<String>()
    private val names = hashMapOf<T, String>()
    private val suggestedNameLastIdx = mutableMapOf<String, Int>()

    operator fun get(declaration: T): String? = names[declaration]

    override fun isReserved(name: String): Boolean = parent.isReserved(name) || name in reserved

    fun declareStableName(
        declaration: T,
        name: String,
    ) {
        names[declaration] = name
        reserved.add(name)
    }

    fun declareFreshName(
        declaration: T,
        suggestedName: String,
    ): String {
        val freshName = findFreshName(makeValidLuaIdentifier(suggestedName))
        declareStableName(declaration, freshName)
        return freshName
    }

    private fun findFreshName(suggestedName: String): String {
        if (!isReserved(suggestedName)) return suggestedName

        var i = suggestedNameLastIdx[suggestedName] ?: 0

        fun candidate() = "${suggestedName}_$i"

        while (isReserved(candidate())) {
            i++
        }
        suggestedNameLastIdx[suggestedName] = i
        return candidate()
    }
}
