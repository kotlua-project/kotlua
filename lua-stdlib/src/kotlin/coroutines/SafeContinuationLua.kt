/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.coroutines

import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.CoroutineSingletons.RESUMED
import kotlin.coroutines.intrinsics.CoroutineSingletons.UNDECIDED

private class SafeContinuationFailure(val exception: Throwable)

@PublishedApi
@SinceKotlin("1.3")
internal actual class SafeContinuation<in T>
internal actual constructor(
    private val delegate: Continuation<T>,
    initialResult: Any?,
) : Continuation<T> {
    @PublishedApi
    internal actual constructor(delegate: Continuation<T>) : this(delegate, UNDECIDED)

    public actual override val context: CoroutineContext
        get() = delegate.context

    private var result: Any? = initialResult

    public actual override fun resumeWith(result: Result<T>) {
        val cur = this.result
        when {
            cur === UNDECIDED -> {
                this.result =
                    if (result.isFailure) {
                        SafeContinuationFailure(result.exceptionOrNull()!!)
                    } else {
                        result.getOrNull()
                    }
            }

            cur === COROUTINE_SUSPENDED -> {
                this.result = RESUMED
                delegate.resumeWith(result)
            }

            else -> throw IllegalStateException("Already resumed")
        }
    }

    @PublishedApi
    internal actual fun getOrThrow(): Any? {
        if (result === UNDECIDED) {
            result = COROUTINE_SUSPENDED
            return COROUTINE_SUSPENDED
        }
        val res = this.result
        return when {
            res === RESUMED -> COROUTINE_SUSPENDED
            res is SafeContinuationFailure -> throw res.exception
            else -> res
        }
    }
}
