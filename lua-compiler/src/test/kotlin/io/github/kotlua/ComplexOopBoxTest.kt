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

class ComplexOopBoxTest : AbstractKotluaTest() {
    @Test
    fun `stateful classes, interfaces, private and protected members, composition, and local functions`() {
        assertBoxOk(
            """
            interface Meter {
                val current: Int

                fun add(delta: Int): Int

                fun addDouble(delta: Int): Int = add(delta * 2)
            }

            interface AuditSink {
                fun record(entry: String)

                fun readLog(): String

                fun recordPair(
                    tag: String,
                    value: Int,
                ) {
                    record(tag + "=" + value)
                }
            }

            abstract class BaseNode(
                protected val name: String,
            ) {
                private var internalSeq: Int = 100
                private val secretTag: String = "BASE"
                protected var weight: Int = 1

                init {
                    bumpSeq(5)
                }

                private fun bumpSeq(d: Int) {
                    internalSeq += d
                }

                private fun tagInfo(): String = secretTag + ":" + internalSeq

                protected open fun computeBias(): Int = weight * 10

                fun baseSummary(): String = tagInfo() + "/" + name

                abstract fun evaluate(x: Int): Int
            }

            open class WeightedCounter(
                name: String,
                initial: Int,
            ) : BaseNode(name),
                Meter {
                private val secretTag: String = "CHILD"
                protected var value: Int = initial
                private var ops: Int = 0

                constructor(name: String) : this(name, 10) {
                    value += 5
                }

                init {
                    weight = 2
                }

                private fun tagInfo(): String = secretTag + "#" + ops

                override val current: Int
                    get() = value

                override fun add(delta: Int): Int {
                    ops++
                    value += delta
                    return value
                }

                override fun computeBias(): Int = super.computeBias() + 3

                override fun evaluate(x: Int): Int {
                    var acc = computeBias()
                    fun applyStep(step: Int) {
                        acc += step + value
                    }
                    applyStep(x)
                    return acc
                }

                fun childDetails(): String = tagInfo() + "/" + baseSummary()
            }

            class BufferSink(
                private val prefix: String,
            ) : AuditSink {
                private var buffer: String = ""
                private var count: Int = 0

                override fun record(entry: String) {
                    if (count > 0) buffer += ","
                    buffer += prefix + ":" + entry
                    count++
                }

                override fun readLog(): String = buffer + "(" + count + ")"
            }

            open class Coordinator(
                protected val meter: Meter,
                private val sink: AuditSink,
                private val node: BaseNode,
            ) {
                private var runs: Int = 0

                constructor(counter: WeightedCounter, sink: AuditSink) : this(counter, sink, counter)

                protected fun recordAndScore(
                    tag: String,
                    delta: Int,
                ): Int {
                    runs++
                    var localBonus = runs
                    fun applyAndLog(amount: Int): Int {
                        val updated = meter.addDouble(amount)
                        localBonus += updated
                        sink.recordPair(tag, updated)
                        return node.evaluate(localBonus)
                    }
                    return applyAndLog(delta)
                }

                open fun execute(
                    stepA: Int,
                    stepB: Int,
                ): Int {
                    val s1 = recordAndScore("A", stepA)
                    val s2 = recordAndScore("B", stepB)
                    return s1 + s2
                }

                fun status(): String = "runs=" + runs + "|log=" + sink.readLog() + "|cur=" + meter.current
            }

            class AuditingCoordinator(
                counter: WeightedCounter,
                sink: AuditSink,
                private val multiplier: Int,
            ) : Coordinator(counter, sink) {
                override fun execute(
                    stepA: Int,
                    stepB: Int,
                ): Int {
                    val res = super.execute(stepA, stepB) * multiplier
                    meter.add(1)
                    return res
                }
            }

            fun box(): String {
                val c1 = WeightedCounter("N1")
                val c2 = WeightedCounter("N2", 30)
                val sink = BufferSink("L")
                val coord = AuditingCoordinator(c1, sink, 2)

                val res = coord.execute(3, 4)
                c2.add(5)
                val c2Eval = c2.evaluate(10)

                val details1 = c1.childDetails()
                val details2 = c2.childDetails()
                val status = coord.status()

                return if (res == 298 &&
                    c2Eval == 68 &&
                    details1 == "CHILD#3/BASE:105/N1" &&
                    details2 == "CHILD#1/BASE:105/N2" &&
                    status == "runs=2|log=L:A=21,L:B=29(2)|cur=30"
                ) {
                    "OK"
                } else {
                    "FAIL:" + res + "|" + c2Eval + "|" + details1 + "|" + details2 + "|" + status
                }
            }
            """.trimIndent(),
        )
    }
}
