package kotlin.lua

import kotlin.coroutines.*
import kotlin.internal.UsedFromCompilerGeneratedCode

@UsedFromCompilerGeneratedCode
internal fun <T> getContinuation(): Continuation<T> { throw Exception("Implemented as intrinsic") }

@Suppress("UNCHECKED_CAST")
@UsedFromCompilerGeneratedCode
internal suspend fun <T> returnIfSuspended(argument: Any?): T {
    return argument as T
}

internal fun <T> interceptContinuationIfNeeded(
    context: CoroutineContext,
    continuation: Continuation<T>
): Continuation<T> = context[ContinuationInterceptor]?.interceptContinuation(continuation) ?: continuation

@UsedFromCompilerGeneratedCode
internal inline suspend fun getCoroutineContext(): CoroutineContext = getContinuation<Any?>().context

@UsedFromCompilerGeneratedCode
internal inline suspend fun <T> suspendCoroutineUninterceptedOrReturnLua(block: (Continuation<T>) -> Any?): T =
    returnIfSuspended<T>(block(getContinuation<T>()))
