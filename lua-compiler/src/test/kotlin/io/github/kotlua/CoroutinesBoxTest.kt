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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CoroutinesBoxTest : AbstractKotluaTest() {
    @Test
    fun `basic suspend functions and delegation`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            suspend fun immediateValue(): Int = 21

            suspend fun delegatingCall(): Int = immediateValue()

            suspend fun withDefault(a: Int, b: Int = 21): Int {
                return a + b
            }

            fun runSync(block: suspend () -> Int): Int {
                var result: Result<Int>? = null
                block.startCoroutine(object : Continuation<Int> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(res: Result<Int>) {
                        result = res
                    }
                })
                return result!!.getOrThrow()
            }

            fun box(): String {
                val r1 = runSync { delegatingCall() }
                if (r1 != 21) return "FAIL delegating: ${'$'}r1"
                val r2 = runSync { withDefault(21) }
                if (r2 != 42) return "FAIL default: ${'$'}r2"
                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `multiple suspension points preserve locals and parameters`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            val pending = ArrayList<Continuation<Int>>()

            suspend fun awaitStep(tag: Int): Int = suspendCoroutine { cont ->
                pending.add(cont)
            }

            suspend fun compute(p1: Int, p2: String): String {
                val a = p1 + 1
                var acc = awaitStep(1)
                val b = p2 + ":"
                acc += awaitStep(2) * 10
                val c = awaitStep(3) * 100
                return "${'$'}b${'$'}{a + acc + c}"
            }

            fun box(): String {
                pending.clear()
                var out: Result<String>? = null
                suspend { compute(4, "res") }.startCoroutine(object : Continuation<String> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<String>) {
                        out = result
                    }
                })

                if (out != null) return "FAIL: finished too early"
                if (pending.size != 1) return "FAIL: expected 1st continuation"
                pending.removeAt(0).resume(2)

                if (out != null) return "FAIL: finished after 1st resume"
                if (pending.size != 1) return "FAIL: expected 2nd continuation"
                pending.removeAt(0).resume(3)

                if (out != null) return "FAIL: finished after 2nd resume"
                if (pending.size != 1) return "FAIL: expected 3rd continuation"
                pending.removeAt(0).resume(5)

                val value = out?.getOrNull() ?: return "FAIL: no result"
                if (value != "res:537") return "FAIL: got ${'$'}value"
                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `safe continuation handles synchronous and asynchronous resume and failure`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            suspend fun syncSuccess(): Int = suspendCoroutine { cont ->
                cont.resume(77)
            }

            suspend fun syncFailure(): Int = suspendCoroutine { cont ->
                cont.resumeWithException(IllegalStateException("sync-err"))
            }

            var saved: Continuation<Int>? = null
            suspend fun asyncStep(): Int = suspendCoroutine { cont ->
                saved = cont
            }

            fun runCatchingCoroutine(block: suspend () -> Int): Result<Int>? {
                var out: Result<Int>? = null
                block.startCoroutine(object : Continuation<Int> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<Int>) {
                        out = result
                    }
                })
                return out
            }

            fun box(): String {
                val r1 = runCatchingCoroutine { syncSuccess() }
                if (r1?.getOrNull() != 77) return "FAIL syncSuccess: ${'$'}r1"

                val r2 = runCatchingCoroutine { syncFailure() }
                if (r2?.exceptionOrNull()?.message != "sync-err") return "FAIL syncFailure: ${'$'}r2"

                saved = null
                var asyncOut: Result<Int>? = null
                suspend { asyncStep() }.startCoroutine(object : Continuation<Int> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<Int>) {
                        asyncOut = result
                    }
                })
                if (asyncOut != null) return "FAIL: async finished early"
                saved!!.resumeWithException(IllegalArgumentException("async-err"))
                if (asyncOut?.exceptionOrNull()?.message != "async-err") return "FAIL asyncFailure: ${'$'}asyncOut"

                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `loops while doWhile for and labeled break continue with suspension`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            val queue = ArrayList<Continuation<Int>>()

            suspend fun step(): Int = suspendCoroutine { queue.add(it) }

            suspend fun testLoops(): String {
                val sb = StringBuilder()

                var i = 0
                while (i < 4) {
                    i++
                    val v = step()
                    if (v == 2) continue
                    if (v == 4) break
                    sb.append(v)
                }

                var j = 0
                do {
                    j++
                    sb.append(step())
                } while (j < 2)

                for (k in 1..3) {
                    sb.append(k).append(':').append(step()).append(';')
                }

                outer@ for (x in 1..3) {
                    for (y in 1..3) {
                        val v = step()
                        if (v == 99) break@outer
                        sb.append(x).append(y)
                    }
                }

                return sb.toString()
            }

            fun box(): String {
                queue.clear()
                var res: Result<String>? = null
                suspend { testLoops() }.startCoroutine(object : Continuation<String> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<String>) {
                        res = result
                    }
                })

                val inputs = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 99)
                for (inp in inputs) {
                    if (queue.isEmpty()) return "FAIL: queue empty at ${'$'}inp"
                    queue.removeAt(0).resume(inp)
                }
                if (queue.isNotEmpty()) return "FAIL: leftover continuations ${'$'}{queue.size}"

                val actual = res?.getOrThrow() ?: return "FAIL: no result"
                val expected = "13561:7;2:8;3:9;11"
                if (actual != expected) return "FAIL: expected ${'$'}expected, got ${'$'}actual"
                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `when expressions and inline non-local return with suspension`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            val queue = ArrayList<Continuation<Int>>()

            suspend fun nextVal(): Int = suspendCoroutine { queue.add(it) }

            inline fun runInline(flag: Boolean, block: () -> Int): Int {
                if (flag) return block()
                return -1
            }

            suspend fun computeWhen(mode: Int): Int {
                val base = when (mode) {
                    1 -> nextVal() + 10
                    2 -> 20
                    else -> nextVal() * 2
                }
                val inlineRes = runInline(true) {
                    val x = nextVal()
                    if (x < 0) return -999
                    x + 1
                }
                return base + inlineRes
            }

            fun drive(mode: Int, vararg inputs: Int): Int {
                queue.clear()
                var out: Result<Int>? = null
                suspend { computeWhen(mode) }.startCoroutine(object : Continuation<Int> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<Int>) {
                        out = result
                    }
                })
                for (v in inputs) {
                    queue.removeAt(0).resume(v)
                }
                return out!!.getOrThrow()
            }

            fun box(): String {
                if (drive(1, 5, 3) != 19) return "FAIL branch 1"
                if (drive(2, 4) != 25) return "FAIL branch 2"
                if (drive(3, 7, -1) != -999) return "FAIL non-local return"
                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `try catch finally and exception propagation across suspension`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            val queue = ArrayList<Continuation<String>>()

            suspend fun step(): String = suspendCoroutine { queue.add(it) }

            suspend fun boom(): String {
                step()
                throw IllegalStateException("boom")
            }

            suspend fun testTryCatchFinally(log: StringBuilder): String {
                try {
                    log.append("try1;")
                    val s = step()
                    if (s == "fail") {
                        boom()
                    }
                    log.append("try2;")
                    return "done"
                } catch (e: IllegalStateException) {
                    log.append("catch(${'$'}{e.message});")
                    val r = step()
                    log.append("afterCatch(${'$'}r);")
                    return "recovered:${'$'}r"
                } finally {
                    log.append("finally1;")
                    val f = step()
                    log.append("finally2(${'$'}f);")
                }
            }

            suspend fun uncaughtOuter(): String {
                return boom()
            }

            fun box(): String {
                queue.clear()
                val log = StringBuilder()
                var res: Result<String>? = null
                suspend { testTryCatchFinally(log) }.startCoroutine(object : Continuation<String> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<String>) {
                        res = result
                    }
                })

                queue.removeAt(0).resume("fail")
                queue.removeAt(0).resume("throw")
                queue.removeAt(0).resume("ok")
                queue.removeAt(0).resume("fin")

                val value = res?.getOrThrow() ?: return "FAIL: no result"
                if (value != "recovered:ok") return "FAIL value: ${'$'}value"
                val expectedLog = "try1;catch(boom);afterCatch(ok);finally1;finally2(fin);"
                if (log.toString() != expectedLog) return "FAIL log: ${'$'}log"

                queue.clear()
                var outerRes: Result<String>? = null
                suspend { uncaughtOuter() }.startCoroutine(object : Continuation<String> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<String>) {
                        outerRes = result
                    }
                })
                queue.removeAt(0).resume("go")
                if (outerRes?.exceptionOrNull()?.message != "boom") {
                    return "FAIL uncaught: ${'$'}outerRes"
                }
                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `createCoroutine lambdas captured variables and callable references`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            val queue = ArrayList<Continuation<Int>>()

            suspend fun yieldInt(): Int = suspendCoroutine { queue.add(it) }

            suspend fun topSuspend(x: Int): Int {
                return x + yieldInt()
            }

            class Boxed(private val base: Int) {
                suspend fun addSuspended(delta: Int): Int {
                    return base + delta + yieldInt()
                }
            }

            fun box(): String {
                queue.clear()
                val imm = 10
                var mut = 1

                val lambda: suspend () -> Int = {
                    val a = yieldInt()
                    mut += a
                    val inner: suspend () -> Int = {
                        yieldInt() + imm + mut
                    }
                    inner() + 100
                }

                var out: Result<Int>? = null
                val created = lambda.createCoroutine(object : Continuation<Int> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<Int>) {
                        out = result
                    }
                })

                if (queue.isNotEmpty() || out != null) return "FAIL: started beforae resume"

                created.resume(Unit)
                queue.removeAt(0).resume(5)
                queue.removeAt(0).resume(4) // 4 + 10 + 6 + 100 = 120
                if (out?.getOrNull() != 120) return "FAIL lambda: ${'$'}out"
                if (mut != 6) return "FAIL mut: ${'$'}mut"

                val ref1: suspend (Int) -> Int = ::topSuspend
                val b = Boxed(30)
                val ref2: suspend (Int) -> Int = b::addSuspended

                var refOut: Result<Int>? = null
                suspend {
                    ref1(10) + ref2(2)
                }.startCoroutine(object : Continuation<Int> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<Int>) {
                        refOut = result
                    }
                })
                queue.removeAt(0).resume(3) // 13
                queue.removeAt(0).resume(5) // 37
                if (refOut?.getOrNull() != 50) return "FAIL refs: ${'$'}refOut"

                // Receiver coroutine
                var recvOut: Result<String>? = null
                val recvBlock: suspend String.() -> String = {
                    this + ":" + yieldInt()
                }
                recvBlock.startCoroutine("hi", object : Continuation<String> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<String>) {
                        recvOut = result
                    }
                })
                queue.removeAt(0).resume(9)
                if (recvOut?.getOrNull() != "hi:9") return "FAIL receiver: ${'$'}recvOut"

                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `coroutineContext and ContinuationInterceptor lifecycle`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            class Tag(val name: String) : AbstractCoroutineContextElement(Key) {
                companion object Key : CoroutineContext.Key<Tag>
            }

            class RecordingInterceptor(val events: ArrayList<String>) :
                AbstractCoroutineContextElement(ContinuationInterceptor),
                ContinuationInterceptor {

                override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
                    events.add("intercept")
                    return object : Continuation<T> {
                        override val context: CoroutineContext get() = continuation.context
                        override fun resumeWith(result: Result<T>) {
                            events.add("dispatch")
                            continuation.resumeWith(result)
                        }
                    }
                }

                override fun releaseInterceptedContinuation(continuation: Continuation<*>) {
                    events.add("release")
                }
            }

            var paused: Continuation<String>? = null
            suspend fun pause(): String = suspendCoroutine { paused = it }

            suspend fun readTagAndPause(): String {
                val first = coroutineContext[Tag]?.name ?: "none"
                val mid = pause()
                val second = coroutineContext[Tag]?.name ?: "none"
                return "${'$'}first:${'$'}mid:${'$'}second"
            }

            fun box(): String {
                val events = ArrayList<String>()
                val interceptor = RecordingInterceptor(events)
                val ctx = Tag("lua") + interceptor

                var out: Result<String>? = null
                suspend { readTagAndPause() }.startCoroutine(object : Continuation<String> {
                    override val context: CoroutineContext get() = ctx
                    override fun resumeWith(result: Result<String>) {
                        out = result
                    }
                })

                paused!!.resume("mid")
                if (out?.getOrNull() != "lua:mid:lua") return "FAIL value: ${'$'}out"

                val trace = events.joinToString(",")
                if (trace != "intercept,dispatch,intercept,dispatch,release,release") return "FAIL trace: ${'$'}trace"
                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `polymorphic and generic bridge suspend methods`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            val queue = ArrayList<Continuation<Int>>()

            suspend fun pauseInt(): Int = suspendCoroutine { queue.add(it) }

            interface Transformer<T, R> {
                suspend fun transform(value: T): R
            }

            open class BaseTransformer : Transformer<Int, String> {
                override suspend fun transform(value: Int): String {
                    val delta = pauseInt()
                    return "base:${'$'}{value + delta}"
                }
            }

            class ChildTransformer : BaseTransformer() {
                override suspend fun transform(value: Int): String {
                    val superRes = super.transform(value)
                    val extra = pauseInt()
                    return "${'$'}superRes+${'$'}extra"
                }
            }

            fun box(): String {
                queue.clear()
                val impl: Transformer<Int, String> = ChildTransformer()
                var out: Result<String>? = null
                suspend { impl.transform(10) }.startCoroutine(object : Continuation<String> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<String>) {
                        out = result
                    }
                })

                queue.removeAt(0).resume(5)
                queue.removeAt(0).resume(2)
                if (out?.getOrNull() != "base:15+2") return "FAIL: ${'$'}out"
                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `suspend fun main entry point`() {
        val luaCode =
            compileToLua(
                """
                import kotlin.coroutines.*

                var result = ""

                suspend fun step(v: String): String = suspendCoroutine { cont ->
                    cont.resume(v)
                }

                suspend fun main() {
                    result = step("OK")
                    result = getResult()
                }

                fun getResult(): String = result
                """.trimIndent(),
            )
        val (_, exports) = loadLuaModule(luaCode)
        val wrapper = exports.get("mainWrapper")
        if (!wrapper.isnil()) {
            wrapper.call()
            val res = exports.get("getResult").call().tojstring()
            assertEquals("OK", res)
        }
    }

    @Test
    fun `deep recursive suspend chain uses trampoline without stack overflow`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            val queue = ArrayList<Continuation<Int>>()

            suspend fun tick(): Int = suspendCoroutine { queue.add(it) }

            suspend fun recurse(n: Int): Int {
                if (n <= 0) return tick()
                return recurse(n - 1) + 1
            }

            fun box(): String {
                queue.clear()
                var out: Result<Int>? = null
                suspend { recurse(500) }.startCoroutine(object : Continuation<Int> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<Int>) {
                        out = result
                    }
                })
                if (queue.size != 1) return "FAIL: queue size ${'$'}{queue.size}"
                queue.removeAt(0).resume(10)
                val value = out?.getOrNull() ?: return "FAIL: no result"
                if (value != 510) return "FAIL: got ${'$'}value"
                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `long double char nullable and unit values survive suspension points`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            val longQueue = ArrayList<Continuation<Long>>()
            val anyQueue = ArrayList<Continuation<Any?>>()
            val unitQueue = ArrayList<Continuation<Unit>>()

            suspend fun nextLong(): Long = suspendCoroutine { longQueue.add(it) }
            suspend fun nextAny(): Any? = suspendCoroutine { anyQueue.add(it) }
            suspend fun nextUnit(): Unit = suspendCoroutine { unitQueue.add(it) }

            suspend fun process(
                initialLong: Long,
                ch: Char,
                maybeNull: String?,
            ): String {
                val a = initialLong + nextLong()
                val n = nextAny()
                nextUnit()
                val b = nextLong()
                val resLong = a - b
                return "${'$'}resLong|${'$'}ch|${'$'}maybeNull|${'$'}n"
            }

            fun box(): String {
                longQueue.clear()
                anyQueue.clear()
                unitQueue.clear()

                var out: Result<String>? = null
                suspend {
                    process(Long.MAX_VALUE - 10L, 'Я', null)
                }.startCoroutine(object : Continuation<String> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<String>) {
                        out = result
                    }
                })

                longQueue.removeAt(0).resume(5L)
                anyQueue.removeAt(0).resume(null)
                unitQueue.removeAt(0).resume(Unit)
                longQueue.removeAt(0).resume(-3L)

                val expected = "${'$'}{Long.MAX_VALUE - 2L}|Я|null|null"
                val actual = out?.getOrNull()
                if (actual != expected) return "FAIL: expected ${'$'}expected, got ${'$'}actual"
                return "OK"
            }
            """.trimIndent(),
        )
    }

    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "kotlua.test.luaExecutable", matches = ".+")
    @Test
    fun `negative zero and NaN double survive suspension`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            var saved: Continuation<Double>? = null
            suspend fun pauseD(): Double = suspendCoroutine { saved = it }

            suspend fun checkDouble(d: Double): String {
                val x = pauseD()
                val negZeroOk = d == 0.0 && 1.0 / d < 0.0
                val nanOk = x.isNaN()
                return "${'$'}negZeroOk:${'$'}nanOk"
            }

            fun box(): String {
                saved = null
                var out: Result<String>? = null
                val negZero = -1.0 / Double.POSITIVE_INFINITY
                suspend { checkDouble(negZero) }.startCoroutine(object : Continuation<String> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<String>) {
                        out = result
                    }
                })
                saved!!.resume(Double.NaN)
                if (out?.getOrNull() != "true:true") return "FAIL: ${'$'}out"
                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `multiple catches nested try finally and break through finally with suspension`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            val queue = ArrayList<Continuation<Int>>()

            suspend fun step(): Int = suspendCoroutine { queue.add(it) }

            suspend fun nestedTryWithBreak(log: StringBuilder): String {
                var i = 0
                while (i < 5) {
                    i++
                    try {
                        val v = step()
                        try {
                            if (v == 1) throw IllegalArgumentException("iae")
                            if (v == 2) throw IllegalStateException("ise")
                            if (v == 3) break
                            log.append("ok(${'$'}v);")
                        } catch (e: IllegalArgumentException) {
                            log.append("innerIae;")
                            step()
                        } finally {
                            log.append("innerFin(${'$'}i,")
                            val f = step()
                            log.append("${'$'}f);")
                        }
                    } catch (e: IllegalStateException) {
                        log.append("outerIse;")
                    } finally {
                        log.append("outerFin(${'$'}i);")
                    }
                }
                return "done:${'$'}i"
            }

            suspend fun finallyOverridesReturn(): String {
                try {
                    step()
                    return "fromTry"
                } finally {
                    val f = step()
                    if (f == 42) return "fromFinally"
                }
            }

            fun box(): String {
                queue.clear()
                val log = StringBuilder()
                var out1: Result<String>? = null
                suspend { nestedTryWithBreak(log) }.startCoroutine(object : Continuation<String> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<String>) {
                        out1 = result
                    }
                })

                // i=1: v=1 -> innerIae -> step(10) -> innerFin step(11) -> outerFin
                queue.removeAt(0).resume(1)
                queue.removeAt(0).resume(10)
                queue.removeAt(0).resume(11)
                // i=2: v=2 -> innerFin step(20) -> outerIse -> outerFin
                queue.removeAt(0).resume(2)
                queue.removeAt(0).resume(20)
                // i=3: v=3 -> break -> innerFin step(30) -> outerFin -> exit loop
                queue.removeAt(0).resume(3)
                queue.removeAt(0).resume(30)

                if (out1?.getOrThrow() != "done:3") return "FAIL out1: ${'$'}out1"
                val expectedLog =
                    "innerIae;innerFin(1,11);outerFin(1);" +
                        "innerFin(2,20);outerIse;outerFin(2);" +
                        "innerFin(3,30);outerFin(3);"
                if (log.toString() != expectedLog) return "FAIL log: ${'$'}log"

                queue.clear()
                var out2: Result<String>? = null
                suspend { finallyOverridesReturn() }.startCoroutine(object : Continuation<String> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<String>) {
                        out2 = result
                    }
                })
                queue.removeAt(0).resume(1)
                queue.removeAt(0).resume(42)
                if (out2?.getOrThrow() != "fromFinally") return "FAIL finallyReturn: ${'$'}out2"

                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `argument evaluation order compound assignments and string templates across suspension`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            val queue = ArrayList<Continuation<Int>>()
            val trace = StringBuilder()

            fun mark(tag: String, value: Int): Int {
                trace.append(tag).append(';')
                return value
            }

            suspend fun ask(tag: String): Int {
                trace.append("ask(").append(tag).append(");")
                return suspendCoroutine { queue.add(it) }
            }

            fun combine(a: Int, b: Int, c: Int, d: Int): Int = a + b * 10 + c * 100 + d * 1000

            suspend fun runAll(): String {
                val combined = combine(
                    mark("a", 1),
                    ask("b"),
                    mark("c", 3),
                    ask("d"),
                )

                var acc = 5
                acc += ask("plus")

                val arr = intArrayOf(10, 20)
                var idxCalls = 0
                fun nextIdx(): Int {
                    idxCalls++
                    trace.append("idx;").append(idxCalls).append(';')
                    return 1
                }
                arr[nextIdx()] += ask("arr")

                val templ = "${'$'}{mark("t1", 7)}-${'$'}{ask("t2")}-${'$'}{mark("t3", 9)}"
                return "${'$'}combined|${'$'}acc|${'$'}{arr[1]}|${'$'}idxCalls|${'$'}templ"
            }

            fun box(): String {
                queue.clear()
                trace.clear()
                var out: Result<String>? = null
                suspend { runAll() }.startCoroutine(object : Continuation<String> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<String>) {
                        out = result
                    }
                })

                queue.removeAt(0).resume(2) // b=2
                queue.removeAt(0).resume(4) // d=4
                queue.removeAt(0).resume(6) // plus=6 -> acc=11
                queue.removeAt(0).resume(8) // arr=8 -> arr[1]=28
                queue.removeAt(0).resume(5) // t2=5

                val expected = "4321|11|28|1|7-5-9"
                val actual = out?.getOrThrow()
                if (actual != expected) return "FAIL value: expected ${'$'}expected, got ${'$'}actual"

                val expectedTrace = "a;ask(b);c;ask(d);ask(plus);idx;1;ask(arr);t1;ask(t2);t3;"
                if (trace.toString() != expectedTrace) return "FAIL trace: ${'$'}trace"

                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `inner class dispatch and extension receivers vararg and defaults with suspension`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            val queue = ArrayList<Continuation<String>>()

            suspend fun pauseStr(): String = suspendCoroutine { queue.add(it) }

            class Outer(private val prefix: String) {
                inner class Inner(private val mid: String) {
                    suspend fun String.formatWith(
                        suffix: String = "end",
                        vararg items: Int,
                    ): String {
                        val s1 = pauseStr()
                        var sum = 0
                        for (x in items) {
                            sum += x
                        }
                        val s2 = pauseStr()
                        return "${'$'}prefix:${'$'}mid:${'$'}this:${'$'}s1:${'$'}sum:${'$'}s2:${'$'}suffix"
                    }

                    suspend fun run(): String = "recv".formatWith(items = intArrayOf(1, 2, 3))
                }
            }

            fun box(): String {
                queue.clear()
                var out: Result<String>? = null
                val inner = Outer("O").Inner("I")
                suspend { inner.run() }.startCoroutine(object : Continuation<String> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<String>) {
                        out = result
                    }
                })

                queue.removeAt(0).resume("A")
                queue.removeAt(0).resume("B")
                val actual = out?.getOrThrow()
                if (actual != "O:I:recv:A:6:B:end") return "FAIL: ${'$'}actual"
                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `inline repeat and let with suspension and non-suspend inner lambda capturing state machine locals`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            val queue = ArrayList<Continuation<Int>>()

            suspend fun next(): Int = suspendCoroutine { queue.add(it) }

            suspend fun compute(): Int {
                var total = 0
                repeat(3) { idx ->
                    val v = next()
                    val fn: () -> Int = { idx * 10 + v }
                    val after = next()
                    total += fn() + after
                }
                return total.let { sum ->
                    sum + next()
                }
            }

            fun box(): String {
                queue.clear()
                var out: Result<Int>? = null
                suspend { compute() }.startCoroutine(object : Continuation<Int> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<Int>) {
                        out = result
                    }
                })

                // idx=0: v=1, after=2 -> 3
                // idx=1: v=3, after=4 -> 13 + 4 = 17
                // idx=2: v=5, after=6 -> 25 + 6 = 31
                // total=51, final next=9 -> 60
                for (v in listOf(1, 2, 3, 4, 5, 6, 9)) {
                    queue.removeAt(0).resume(v)
                }
                if (out?.getOrThrow() != 60) return "FAIL: ${'$'}out"
                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `double resume and direct suspendCoroutineUninterceptedOrReturn errors`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*
            import kotlin.coroutines.intrinsics.*

            var saved: Continuation<Int>? = null

            suspend fun immediateUnintercepted(): Int = suspendCoroutineUninterceptedOrReturn { 123 }

            suspend fun pausedSafe(): Int = suspendCoroutine { saved = it }

            fun box(): String {
                var immediateOut: Result<Int>? = null
                suspend { immediateUnintercepted() }.startCoroutine(object : Continuation<Int> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<Int>) {
                        immediateOut = result
                    }
                })
                if (immediateOut?.getOrNull() != 123) return "FAIL immediate: ${'$'}immediateOut"

                saved = null
                var safeOut: Result<Int>? = null
                suspend { pausedSafe() }.startCoroutine(object : Continuation<Int> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<Int>) {
                        safeOut = result
                    }
                })
                saved!!.resume(10)
                if (safeOut?.getOrNull() != 10) return "FAIL first resume: ${'$'}safeOut"
                var doubleResumeFailed = false
                try {
                    saved!!.resume(20)
                } catch (e: IllegalStateException) {
                    doubleResumeFailed = true
                }
                if (!doubleResumeFailed) return "FAIL: expected IllegalStateException on double resume"

                var createdOut: Result<Int>? = null
                val created = suspend { 5 }.createCoroutine(object : Continuation<Int> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<Int>) {
                        createdOut = result
                    }
                })
                created.resume(Unit)
                if (createdOut?.getOrNull() != 5) return "FAIL created: ${'$'}createdOut"
                var secondCreatedFailed = false
                try {
                    created.resume(Unit)
                } catch (e: IllegalStateException) {
                    secondCreatedFailed = true
                }
                if (!secondCreatedFailed) return "FAIL: expected IllegalStateException on completed CreatedCoroutine"

                return "OK"
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `is SuspendFunction check and local suspend function`() {
        assertBoxOk(
            """
            import kotlin.coroutines.*

            var saved: Continuation<Int>? = null
            suspend fun waitInt(): Int = suspendCoroutine { saved = it }

            fun checkType(x: Any?): Boolean = x is Function1<*, *>

            suspend fun outer(): Int {
                suspend fun localHelper(k: Int): Int {
                    return k + waitInt()
                }
                return localHelper(10) + localHelper(20)
            }

            fun box(): String {
                val f: Any = suspend { waitInt() }
                if (f !is Function0<*> && f.toString().isEmpty()) return "FAIL type"

                saved = null
                var out: Result<Int>? = null
                suspend { outer() }.startCoroutine(object : Continuation<Int> {
                    override val context: CoroutineContext get() = EmptyCoroutineContext
                    override fun resumeWith(result: Result<Int>) {
                        out = result
                    }
                })
                saved!!.resume(1)
                saved!!.resume(2)
                if (out?.getOrThrow() != 33) return "FAIL local suspend: ${'$'}out"
                return "OK"
            }
            """.trimIndent(),
        )
    }
}
