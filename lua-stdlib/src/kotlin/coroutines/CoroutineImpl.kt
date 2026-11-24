package kotlin.coroutines

import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.internal.UsedFromCompilerGeneratedCode
import kotlin.lua.interceptContinuationIfNeeded

@PublishedApi
@UsedFromCompilerGeneratedCode
internal abstract class CoroutineImpl(
    @PublishedApi internal val resultContinuation: Continuation<Any?>?,
) : Continuation<Any?> {
    @PublishedApi
    @UsedFromCompilerGeneratedCode
    internal var state: Int = 0

    @PublishedApi
    @UsedFromCompilerGeneratedCode
    internal var exceptionState: Int = -1

    @PublishedApi
    @UsedFromCompilerGeneratedCode
    internal var result: Any? = null

    @PublishedApi
    @UsedFromCompilerGeneratedCode
    internal var exception: Throwable? = null

    private var intercepted: Continuation<Any?>? = null

    public override val context: CoroutineContext
        get() = resultContinuation!!.context

    @PublishedApi
    internal fun facade(): Continuation<Any?> {
        var c = intercepted
        if (c == null) {
            c = interceptContinuationIfNeeded(context, this)
            intercepted = c
        }
        return c
    }

    @UsedFromCompilerGeneratedCode
    protected abstract fun doResume(data: Any?, exception: Throwable?): Any?

    public override fun resumeWith(result: Result<Any?>) {
        var current: CoroutineImpl = this
        var currentResult: Result<Any?> = result
        while (true) {
            val completion = current.resultContinuation!!
            val outcome: Result<Any?> =
                try {
                    val r =
                        if (currentResult.isSuccess) {
                            current.result = currentResult.getOrNull()
                            current.doResume(current.result, null)
                        } else {
                            val ex = currentResult.exceptionOrNull()!!
                            if (current.exceptionState < 0) {
                                throw ex
                            }
                            current.exception = ex
                            current.state = current.exceptionState
                            current.doResume(null, ex)
                        }
                    if (r === COROUTINE_SUSPENDED) return
                    Result.success(r)
                } catch (e: Throwable) {
                    Result.failure(e)
                }

            val interceptedCont = current.intercepted
            if (interceptedCont != null && interceptedCont !== current) {
                current.context[ContinuationInterceptor]?.releaseInterceptedContinuation(interceptedCont)
                current.intercepted = null
            }

            if (completion is CoroutineImpl) {
                current = completion
                currentResult = outcome
            } else {
                completion.resumeWith(outcome)
                return
            }
        }
    }
}

@UsedFromCompilerGeneratedCode
internal fun makeMainContinuation(): Continuation<Unit> =
    object : Continuation<Unit> {
        override val context: CoroutineContext
            get() = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            if (result.isFailure) {
                throw result.exceptionOrNull()!!
            }
        }
    }