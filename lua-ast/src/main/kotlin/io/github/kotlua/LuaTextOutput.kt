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

class LuaTextOutput(
    private val indentString: String = "    ",
) {
    private val out = StringBuilder()

    var line: Int = 1
        private set

    var column: Int = 1
        private set

    var indentLevel: Int = 0
        private set

    private var needsIndent = false

    fun indentIn() {
        indentLevel++
    }

    fun indentOut() {
        if (indentLevel > 0) indentLevel--
    }

    inline fun withIndent(block: () -> Unit) {
        indentIn()
        try {
            block()
        } finally {
            indentOut()
        }
    }

    fun newline() {
        out.append('\n')
        line++
        column = 1
        needsIndent = true
    }

    private fun applyIndent() {
        if (!needsIndent) return
        needsIndent = false

        if (indentLevel > 0) {
            repeat(indentLevel) {
                out.append(indentString)
            }
            column += indentString.length * indentLevel
        }
    }

    fun print(s: String) {
        if (s.isEmpty()) return
        applyIndent()

        val lastNewline = s.lastIndexOf('\n')
        if (lastNewline != -1) {
            out.append(s)
            val newlinesCount = s.count { it == '\n' }
            line += newlinesCount
            column = s.length - lastNewline
        } else {
            out.append(s)
            column += s.length
        }
    }

    fun print(c: Char) {
        if (c == '\n') {
            newline()
        } else {
            applyIndent()
            out.append(c)
            column++
        }
    }

    fun println(s: String = "") {
        if (s.isNotEmpty()) print(s)
        newline()
    }

    fun println(c: Char) {
        print(c)
        newline()
    }

    override fun toString(): String = out.toString()
}
