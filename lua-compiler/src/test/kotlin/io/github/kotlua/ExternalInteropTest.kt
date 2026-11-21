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

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ExternalInteropTest : AbstractKotluaTest() {
    @Test
    fun `lua import module and top level functions`() {
        assertBoxOk(
            kotlinCode =
                """
                @LuaImport(module = "math_mod")
                external object MathMod {
                    val piApprox: Double
                    var offset: Int

                    fun add(a: Int, b: Int): Int

                    @LuaName("mul_values")
                    fun mul(a: Int, b: Int): Int
                }

                @LuaImport(module = "math_mod")
                external fun add(a: Int, b: Int): Int

                @LuaImport(module = "math_mod")
                @LuaName("mul_values")
                external fun multiply(a: Int, b: Int): Int

                fun box(): String {
                    val sum1 = MathMod.add(2, 3)
                    val sum2 = add(10, 20)
                    val prod1 = MathMod.mul(3, 4)
                    val prod2 = multiply(5, 6)
                    val initialOffset = MathMod.offset
                    MathMod.offset = 100
                    val updatedOffset = MathMod.offset

                    return if (
                        sum1 == 5 &&
                        sum2 == 30 &&
                        prod1 == 12 &&
                        prod2 == 30 &&
                        MathMod.piApprox > 3.1 &&
                        initialOffset == 7 &&
                        updatedOffset == 100
                    ) "OK" else "FAIL"
                }
                """.trimIndent(),
            luaFiles =
                mapOf(
                    "math_mod.lua" to
                        """
                        local M = {
                            piApprox = 3.14,
                            offset = 7,
                        }
                        function M.add(a, b)
                            return a + b
                        end
                        function M.mul_values(a, b)
                            return a * b
                        end
                        return M
                        """.trimIndent(),
                ),
        )
    }

    @Test
    fun `lua import custom code and lua method`() {
        assertBoxOk(
            kotlinCode =
                $$"""
                @LuaImport(code = "require('service_registry').getService('Players')")
                external object PlayersService {
                    val serviceName: String

                    @LuaMethod
                    fun getCount(): Int

                    @LuaMethod
                    fun formatPlayer(name: String): String
                }

                fun box(): String {
                    val name = PlayersService.serviceName
                    val count = PlayersService.getCount()
                    val formatted = PlayersService.formatPlayer("Alex")
                    return if (name == "Players" && count == 42 && formatted == "Players:Alex") "OK" else "FAIL:$name:$count:$formatted"
                }
                """.trimIndent(),
            luaFiles =
                mapOf(
                    "service_registry.lua" to
                        """
                        local Registry = {}
                        function Registry.getService(name)
                            local svc = { serviceName = name, _count = 42 }
                            function svc:getCount()
                                return self._count
                            end
                            function svc:formatPlayer(player)
                                return self.serviceName .. ":" .. player
                            end
                            return svc
                        end
                        return Registry
                        """.trimIndent(),
                ),
        )
    }

    @Test
    fun `external class constructor methods properties and companion`() {
        assertBoxOk(
            kotlinCode =
                $$"""
                @LuaImport("counter_cls")
                external class Counter @LuaName("new") constructor(initial: Int) {
                    var value: Int

                    fun inc(delta: Int): Int

                    @LuaFunction
                    fun kind(): String

                    companion object {
                        fun defaultStart(): Int
                    }
                }

                fun box(): String {
                    val start = Counter.defaultStart()
                    val c = Counter(start)
                    val v1 = c.value
                    val afterInc = c.inc(5)
                    c.value = 100
                    val v2 = c.value
                    val k = c.kind()
                    return if (start == 10 && v1 == 10 && afterInc == 15 && v2 == 100 && k == "counter") {
                        "OK"
                    } else {
                        "FAIL:$start:$v1:$afterInc:$v2:$k"
                    }
                }
                """.trimIndent(),
            luaFiles =
                mapOf(
                    "counter_cls.lua" to
                        """
                        local Counter = {}
                        Counter.__index = Counter

                        function Counter.new(initial)
                            return setmetatable({ value = initial }, Counter)
                        end

                        function Counter:inc(delta)
                            self.value = self.value + delta
                            return self.value
                        end

                        function Counter.kind()
                            return "counter"
                        end

                        function Counter.defaultStart()
                            return 10
                        end

                        return Counter
                        """.trimIndent(),
                ),
        )
    }

    @Test
    fun `lua global dotted name and external vararg`() {
        assertBoxOk(
            kotlinCode =
                $$"""
                @LuaGlobal
                external object string {
                    fun upper(s: String): String
                    fun format(fmt: String, vararg args: Any?): String
                }

                @LuaGlobal
                @LuaName("string.lower")
                external fun toLowerGlobal(s: String): String

                @LuaGlobal
                @LuaName("table.concat")
                external fun joinTable(tbl: Any?, sep: String): String

                @LuaGlobal
                @LuaName("_VERSION")
                external val luaVersion: String

                fun box(): String {
                    val u = string.upper("kotlua")
                    val l = toLowerGlobal("KOTLUA")
                    val fmt1 = string.format("%s-%d", "v", 5)
                    val parts = arrayOf<Any?>("a", 1, "b")
                    val fmt2 = string.format("%s:%d:%s", *parts)
                    val hasVersion = luaVersion.length > 0
                    return if (u == "KOTLUA" && l == "kotlua" && fmt1 == "v-5" && fmt2 == "a:1:b" && hasVersion) {
                        "OK"
                    } else {
                        "FAIL:$u:$l:$fmt1:$fmt2:$hasVersion"
                    }
                }
                """.trimIndent(),
        )
    }

    @Test
    fun `unused lua import is removed by dce`() {
        val code =
            """
            @LuaImport("missing_module_should_not_be_required")
            external object UnusedModule {
                fun ping(): Int
            }

            fun box(): String = "OK"
            """.trimIndent()
        val luaCode = compileToLua(code)
        assertFalse("missing_module_should_not_be_required" in luaCode, "Unused @LuaImport should be removed by DCE:\n$luaCode")
        assertBoxOk(code)
    }

    @Test
    fun `lua global external object with LuaMultiReturn`() {
        assertBoxOk(
            kotlinCode =
                $$"""
                @LuaGlobal
                external object string {
                    @LuaMultiReturn
                    fun find(s: String, pattern: String): Pair<Int?, Int?>
                }

                fun box(): String {
                    val (startIdx, endIdx) = string.find("hello kotlua", "kotlua")
                    val (missStart, missEnd) = string.find("hello", "xyz")
                    val (_, onlyEnd) = string.find("abcdef", "cd")
                    val packed = string.find("123-456", "-")

                    val okFound = startIdx == 7 && endIdx == 12
                    val okMiss = missStart == null && missEnd == null
                    val okSkip = onlyEnd == 4
                    val okPacked = packed.first == 4 && packed.second == 4

                    return if (okFound && okMiss && okSkip && okPacked) {
                        "OK"
                    } else {
                        "FAIL:$startIdx:$endIdx:$missStart:$missEnd:$onlyEnd:$packed"
                    }
                }
                """.trimIndent(),
        )
    }

    @Test
    fun `lua import external object and class with LuaMultiReturn`() {
        assertBoxOk(
            kotlinCode =
                $$"""
                @LuaImport("multi_service")
                external object MultiService {
                    @LuaMultiReturn
                    fun parse(input: String): Triple<Boolean, Int, String>
                }

                @LuaImport("multi_service")
                external class Parser @LuaName("new") constructor(prefix: String) {
                    @LuaMultiReturn
                    fun splitFirst(input: String): Pair<String, String>
                }

                fun box(): String {
                    val (ok1, code1, msg1) = MultiService.parse("good")
                    val (_, code2, _) = MultiService.parse("bad")
                    val packedTriple = MultiService.parse("good")

                    val parser = Parser(":")
                    val (left, right) = parser.splitFirst("key:value")
                    val packedPair = parser.splitFirst("a:b")

                    val valid = ok1 && code1 == 200 && msg1 == "ok" &&
                        code2 == 400 &&
                        packedTriple.first && packedTriple.second == 200 && packedTriple.third == "ok" &&
                        left == "key" && right == "value" &&
                        packedPair.first == "a" && packedPair.second == "b"

                    return if (valid) "OK" else "FAIL:$ok1:$code1:$msg1:$code2:$left:$right"
                }
                """.trimIndent(),
            luaFiles =
                mapOf(
                    "multi_service.lua" to
                        """
                        local M = {}
                        M.__index = M

                        function M.parse(input)
                            if input == "good" then
                                return true, 200, "ok"
                            end
                            return false, 400, "err"
                        end

                        function M.new(prefix)
                            return setmetatable({ prefix = prefix }, M)
                        end

                        function M:splitFirst(input)
                            local idx = string.find(input, self.prefix, 1, true)
                            return string.sub(input, 1, idx - 1), string.sub(input, idx + #self.prefix)
                        end

                        return M
                        """.trimIndent(),
                ),
        )
    }
}
