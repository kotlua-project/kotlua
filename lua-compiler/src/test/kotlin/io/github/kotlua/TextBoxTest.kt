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

import org.junit.jupiter.api.Test

class TextBoxTest : AbstractKotluaTest() {
    @Test
    fun `StringBuilder append and basic operations`() =
        assertBoxOk(
            """
            fun box(): String {
                val sb = StringBuilder()
                if (sb.length != 0 || sb.toString() != "") return "FAIL: empty"

                sb.append(true)
                    .append(' ')
                    .append(42)
                    .append(' ')
                    .append((-7).toByte())
                    .append(' ')
                    .append(123.toShort())
                    .append(' ')
                    .append(999L)
                    .append(' ')
                    .append(null as String?)
                    .append(' ')
                    .append(null as CharSequence?)
                if (sb.toString() != "true 42 -7 123 999 null null") {
                    return "FAIL: primitives=" + sb.toString()
                }

                sb.clear()
                if (sb.length != 0 || sb.toString() != "") return "FAIL: clear"

                sb.append("abcdef", 1, 4)
                sb.appendRange(charArrayOf('x', 'y', 'z', 'w'), 1, 3)
                sb.append(charArrayOf('!', '?'))
                if (sb.toString() != "bcdyz!?") return "FAIL: ranges=" + sb.toString()

                val sbLines = StringBuilder(16)
                sbLines.appendLine(10).appendLine(20.toByte()).appendLine(30.toShort())
                if (sbLines.toString() != "10\n20\n30\n") return "FAIL: appendLine=" + sbLines.toString()

                val fromCs = StringBuilder(sb as CharSequence)
                fromCs.trimToSize()
                fromCs.ensureCapacity(64)
                if (fromCs.capacity() != 7 || fromCs.toString() != "bcdyz!?") {
                    return "FAIL: fromCs=" + fromCs.toString()
                }

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `StringBuilder insert delete setRange and setLength`() =
        assertBoxOk(
            """
            fun box(): String {
                val sb = StringBuilder("hello world")
                sb.insert(5, ",")
                if (sb.toString() != "hello, world") return "FAIL: insert str"

                sb.insert(0, 123)
                sb.insert(3, '!')
                sb.insert(sb.length, true)
                if (sb.toString() != "123!hello, worldtrue") return "FAIL: insert pos=" + sb.toString()

                sb.insertRange(4, charArrayOf('a', 'b', 'c', 'd'), 1, 3)
                sb.insertRange(0, "XYZ--" as CharSequence, 0, 3)
                if (sb.toString() != "XYZ123!bchello, worldtrue") return "FAIL: insertRange=" + sb.toString()

                sb[0] = 'W'
                sb.deleteAt(1)
                sb.deleteRange(2, 5)
                if (sb.toString() != "WZ!bchello, worldtrue") return "FAIL: delete=" + sb.toString()

                sb.setRange(2, 10, "X")
                if (sb.toString() != "WZX, worldtrue") return "FAIL: setRange=" + sb.toString()

                sb.deleteRange(3, 100)
                if (sb.toString() != "WZX") return "FAIL: deleteRange clamp=" + sb.toString()

                sb.setLength(2)
                if (sb.toString() != "WZ") return "FAIL: shrink=" + sb.toString()

                sb.setLength(4)
                if (sb.length != 4 || sb[2] != '\u0000' || sb[3] != '\u0000') {
                    return "FAIL: grow len=" + sb.length
                }

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `StringBuilder indexOf lastIndexOf substring and toCharArray`() =
        assertBoxOk(
            """
            fun box(): String {
                val sb = StringBuilder("ababa")
                if (sb.indexOf("aba") != 0) return "FAIL: indexOf 0"
                if (sb.indexOf("aba", 1) != 2) return "FAIL: indexOf 1"
                if (sb.indexOf("xyz") != -1) return "FAIL: indexOf missing"
                if (sb.indexOf("") != 0 || sb.indexOf("", 10) != 5) return "FAIL: indexOf empty"

                if (sb.lastIndexOf("aba") != 2) return "FAIL: lastIndexOf"
                if (sb.lastIndexOf("aba", 1) != 0) return "FAIL: lastIndexOf 1"
                if (sb.lastIndexOf("") != 5 || sb.lastIndexOf("", -1) != -1) return "FAIL: lastIndexOf empty"

                if (sb.substring(2) != "aba") return "FAIL: substring(2)"
                if (sb.substring(1, 4) != "bab") return "FAIL: substring(1,4)"
                if (sb.subSequence(0, 3).toString() != "aba") return "FAIL: subSequence"

                val dst = CharArray(5) { '.' }
                sb.toCharArray(dst, 1, 1, 4)
                if (dst.concatToString() != ".bab.") return "FAIL: toCharArray=" + dst.concatToString()

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `StringBuilder reverse preserves multi-byte UTF-8`() =
        assertBoxOk(
            """
            fun box(): String {
                val simple = StringBuilder("abcde").reverse().toString()
                if (simple != "edcba") return "FAIL: simple=" + simple

                val paired = StringBuilder("a\uD83D\uDE00b").reverse().toString()
                if (paired != "b\uD83D\uDE00a") return "FAIL: paired"

                val cyrillic = StringBuilder("Привет").reverse().toString()
                if (cyrillic != "тевирП") return "FAIL: cyrillic=" + cyrillic

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `StringBuilder bounds and argument validation`() =
        assertBoxOk(
            """
            fun box(): String {
                val sb = StringBuilder("abc")
                try {
                    sb[-1]
                    return "FAIL: get -1"
                } catch (_: IndexOutOfBoundsException) {
                }
                try {
                    sb[3]
                    return "FAIL: get 3"
                } catch (_: IndexOutOfBoundsException) {
                }
                try {
                    sb.insert(4, "x")
                    return "FAIL: insert 4"
                } catch (_: IndexOutOfBoundsException) {
                }
                try {
                    sb.setLength(-1)
                    return "FAIL: setLength -1"
                } catch (_: IllegalArgumentException) {
                }
                try {
                    sb.deleteRange(2, 1)
                    return "FAIL: deleteRange 2..1"
                } catch (_: IllegalArgumentException) {
                }
                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `Collection joinToString and AbstractCollection toString`() =
        assertBoxOk(
            """
            fun box(): String {
                val list = listOf(1, 2, 3)
                if (list.toString() != "[1, 2, 3]") return "FAIL: list.toString=" + list.toString()

                val joined = listOf("a", "b", "c").joinToString(separator = "-", prefix = "<", postfix = ">")
                if (joined != "<a-b-c>") return "FAIL: joined=" + joined

                val limited = listOf(1, 2, 3, 4, 5).joinToString(limit = 3, truncated = "...")
                if (limited != "1, 2, 3, ...") return "FAIL: limited=" + limited

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `Char ASCII case predicates and category`() =
        assertBoxOk(
            """
            fun box(): String {
                if ('A'.lowercase() != "a" || 'A'.lowercaseChar() != 'a') return "FAIL: lowercase"
                if ('a'.uppercase() != "A" || 'a'.uppercaseChar() != 'A') return "FAIL: uppercase"
                if ('a'.titlecaseChar() != 'A') return "FAIL: titlecaseChar"

                if (!'A'.isLetter() || !'A'.isUpperCase() || 'A'.isLowerCase()) return "FAIL: A predicates"
                if (!'z'.isLetter() || !'z'.isLowerCase() || 'z'.isUpperCase()) return "FAIL: z predicates"
                if (!'5'.isDigit() || !'5'.isLetterOrDigit() || '5'.isLetter()) return "FAIL: 5 predicates"
                if (!' '.isWhitespace() || !'\t'.isWhitespace() || !'\n'.isWhitespace() || 'x'.isWhitespace()) {
                    return "FAIL: whitespace"
                }
                if (!'\u0000'.isISOControl() || !'\u007F'.isISOControl() || 'A'.isISOControl()) {
                    return "FAIL: isoControl"
                }
                if ('A'.isTitleCase() || !'A'.isDefined()) return "FAIL: title/defined"

                if ('A'.category != CharCategory.UPPERCASE_LETTER) return "FAIL: cat A"
                if ('a'.category != CharCategory.LOWERCASE_LETTER) return "FAIL: cat a"
                if ('0'.category != CharCategory.DECIMAL_DIGIT_NUMBER) return "FAIL: cat 0"
                if (' '.category != CharCategory.SPACE_SEPARATOR) return "FAIL: cat space"
                if ('\n'.category != CharCategory.CONTROL) return "FAIL: cat nl"
                if ('$'.category != CharCategory.CURRENCY_SYMBOL) return "FAIL: cat dollar"
                if ('A' !in CharCategory.UPPERCASE_LETTER) return "FAIL: cat contains"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `Char Unicode predicates category and case mapping`() =
        assertBoxOk(
            """
            fun box(): String {
                if ('\u0130'.lowercase() != "\u0069\u0307") return "FAIL: U+0130 lowercase"
                if ('\u0130'.lowercaseChar() != 'i') return "FAIL: U+0130 lowercaseChar"
                if ('\uFB00'.uppercase() != "FF") return "FAIL: U+FB00 uppercase"
                if ('\uFB00'.uppercaseChar() != '\uFB00') return "FAIL: U+FB00 uppercaseChar"

                if (!'\u01C5'.isTitleCase()) return "FAIL: isTitleCase"
                if ('\u01C5'.category != CharCategory.TITLECASE_LETTER) return "FAIL: cat titlecase"
                if ('\u01C6'.titlecaseChar() != '\u01C5') return "FAIL: titlecaseChar U+01C6"

                if ('Б'.lowercaseChar() != 'б' || 'я'.uppercaseChar() != 'Я') return "FAIL: cyrillic case"
                if (!'Б'.isLetter() || !'Б'.isUpperCase() || !'я'.isLowerCase()) return "FAIL: cyrillic pred"
                if (!'\u0665'.isDigit() || !'\u0665'.isLetterOrDigit()) return "FAIL: arabic digit"
                if (!'\u00A0'.isWhitespace()) return "FAIL: nbsp whitespace"
                if ('\uFFFF'.isDefined()) return "FAIL: U+FFFF defined"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `Char code and Char factory functions`() =
        assertBoxOk(
            """
            fun box(): String {
                if ('A'.code != 65) return "FAIL: code"
                if (Char(65) != 'A') return "FAIL: Char(Int)"
                if (Char(66.toUShort()) != 'B') return "FAIL: Char(UShort)"
                try {
                    Char(-1)
                    return "FAIL: Char(-1)"
                } catch (_: IllegalArgumentException) {
                }
                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `String slicing repeat and CharArray conversions`() =
        assertBoxOk(
            """
            fun box(): String {
                val arr = charArrayOf('k', 'o', 't', 'l', 'u', 'a')
                if (arr.concatToString() != "kotlua") return "FAIL: concatToString"
                if (arr.concatToString(1, 4) != "otl") return "FAIL: concatToString range"

                val back = "kotlua".toCharArray(2, 5)
                if (back.concatToString() != "tlu") return "FAIL: toCharArray range"

                val dst = CharArray(5) { '_' }
                val ret = "hello".toCharArray(dst, 1, 1, 4)
                if (ret !== dst || dst.concatToString() != "_ell_") return "FAIL: toCharArray dst"

                if ("abcdef".substring(2) != "cdef") return "FAIL: substring(2)"
                if ("abcdef".substring(1, 4) != "bcd") return "FAIL: substring(1,4)"
                if ("ab".repeat(3) != "ababab" || "ab".repeat(0) != "") return "FAIL: repeat"

                try {
                    "ab".repeat(-1)
                    return "FAIL: repeat -1"
                } catch (_: IllegalArgumentException) {
                }

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `String search replace startsWith endsWith and regionMatches`() =
        assertBoxOk(
            """
            fun box(): String {
                val s = "ababa"
                if (s.indexOf('b') != 1 || s.lastIndexOf('b') != 3) return "FAIL: char indexOf"
                if (s.indexOf("ba") != 1 || s.lastIndexOf("ba") != 3) return "FAIL: str indexOf"
                if (s.indexOf("BA", ignoreCase = true) != 1) return "FAIL: ignoreCase indexOf"

                if ("foo-bar-foo".replace('-', '_') != "foo_bar_foo") return "FAIL: replace char"
                if ("Foo-foo".replace("foo", "baz", ignoreCase = true) != "baz-baz") return "FAIL: replace str"
                if ("ab".replace("", "-") != "-a-b-") return "FAIL: replace empty"
                if ("foo-foo".replaceFirst("foo", "bar") != "bar-foo") return "FAIL: replaceFirst"

                if (!"Kotlin".startsWith("kot", ignoreCase = true)) return "FAIL: startsWith"
                if (!"Kotlin".startsWith("TL", 2, ignoreCase = true)) return "FAIL: startsWith offset"
                if (!"Kotlin".endsWith("LIN", ignoreCase = true)) return "FAIL: endsWith"
                if (!"Hello".regionMatches(1, "ELL", 0, 3, ignoreCase = true)) return "FAIL: regionMatches"
                if ("Hello".regionMatches(0, "He", 0, -1)) return "FAIL: negative len"

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `String case conversion contentEquals and case insensitive compare`() =
        assertBoxOk(
            """
            fun box(): String {
                if ("Abc".uppercase() != "ABC" || "AbC".lowercase() != "abc") return "FAIL: case"

                val a: String? = "Hello"
                val b: String? = "hello"
                val n: String? = null
                if (!a.equals(b, ignoreCase = true)) return "FAIL: equals ignoreCase"
                if (a.equals(n, ignoreCase = true) || !n.equals(null, ignoreCase = true)) return "FAIL: equals null"

                val sb: CharSequence = StringBuilder("HELLO")
                if (!(a as CharSequence?).contentEquals(sb, ignoreCase = true)) return "FAIL: contentEquals ignoreCase"
                if ((a as CharSequence?).contentEquals(sb)) return "FAIL: contentEquals strict"

                if ("abc".compareTo("ABC", ignoreCase = true) != 0) return "FAIL: compareTo ignoreCase"
                if (String.CASE_INSENSITIVE_ORDER.compare("abc", "ABD") >= 0) return "FAIL: comparator"

                if (!"TRUE".toBoolean() || "false".toBoolean() || (null as String?).toBoolean()) {
                    return "FAIL: toBoolean"
                }

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `String number parsing and toString with radix`() =
        assertBoxOk(
            """
            fun box(): String {
                if ("42".toInt() != 42 || "-42".toInt() != -42 || "+15".toInt() != 15) return "FAIL: toInt"
                if ("-2147483648".toInt() != Int.MIN_VALUE || "2147483647".toInt() != Int.MAX_VALUE) {
                    return "FAIL: Int bounds"
                }
                if ("ff".toInt(16) != 255 || "-101".toInt(2) != -5 || "z".toInt(36) != 35) return "FAIL: toInt radix"
                if ("127".toByte() != 127.toByte() || "-128".toByte() != (-128).toByte()) return "FAIL: toByte"
                if ("32767".toShort() != 32767.toShort() || "-32768".toShort() != (-32768).toShort()) return "FAIL: toShort"
                if ("123456789".toLong() != 123456789L) return "FAIL: toLong"

                if (255.toString(16) != "ff" || (-5).toString(2) != "-101") return "FAIL: toString radix"
                if (Int.MIN_VALUE.toString(16) != "-80000000") return "FAIL: MIN_VALUE radix"
                if ((-1).toByte().toString(16) != "-1" || (-15).toShort().toString(16) != "-f") {
                    return "FAIL: byte/short radix"
                }

                if ("3.5".toDouble() != 3.5 || "1.25".toFloat() != 1.25f) return "FAIL: float/double"
                if (!"NaN".toDouble().isNaN()) return "FAIL: NaN"
                if ("Infinity".toDouble() != Double.POSITIVE_INFINITY) return "FAIL: +Inf"
                if ("-Infinity".toDouble() != Double.NEGATIVE_INFINITY) return "FAIL: -Inf"
                if ("0x10".toDoubleOrNull() != null || "abc".toDoubleOrNull() != null) return "FAIL: invalid double"

                try {
                    "999999999999999".toInt()
                    return "FAIL: overflow"
                } catch (_: NumberFormatException) {
                }
                try {
                    10.toString(37)
                    return "FAIL: invalid radix"
                } catch (_: IllegalArgumentException) {
                }

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `UTF-8 encodeToByteArray and decodeToString`() =
        assertBoxOk(
            """
            fun box(): String {
                val ascii = "Hello".encodeToByteArray()
                if (ascii.size != 5 || ascii[0] != 72.toByte() || ascii.decodeToString() != "Hello") {
                    return "FAIL: ascii"
                }

                val utf8 = "Привет, €!".encodeToByteArray()
                if (utf8.decodeToString() != "Привет, €!") return "FAIL: utf8 roundtrip"

                val sub = utf8.decodeToString(startIndex = 0, endIndex = 4)
                if (sub != "Пр") return "FAIL: utf8 sub=" + sub

                return "OK"
            }
            """.trimIndent(),
        )

    @Test
    fun `UTF-8 invalid sequences replace or throw`() =
        assertBoxOk(
            """
            fun box(): String {
                val invalid = byteArrayOf(0x41, 0x80.toByte(), 0x42)
                if (invalid.decodeToString() != "A\uFFFDB") return "FAIL: replace invalid"

                val overlong = byteArrayOf(0xC0.toByte(), 0xAF.toByte())
                if (overlong.decodeToString() != "\uFFFD\uFFFD") return "FAIL: replace overlong"

                try {
                    invalid.decodeToString(throwOnInvalidSequence = true)
                    return "FAIL: expected CharacterCodingException"
                } catch (_: CharacterCodingException) {
                }

                return "OK"
            }
            """.trimIndent(),
        )
}
