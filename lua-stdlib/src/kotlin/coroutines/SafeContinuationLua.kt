/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.coroutines

@PublishedApi
@SinceKotlin("1.3")
internal actual class SafeContinuation<in T>
    internal actual constructor(
        private val delegate: Continuation<T>,
        initialResult: Any?,
    ) : Continuation<T> {
        @PublishedApi
        internal actual constructor(delegate: Continuation<T>) : this(delegate, null)

        public actual override val context: CoroutineContext
            get() = delegate.context

        private var result: Any? = initialResult

        public actual override fun resumeWith(result: Result<T>) {
            TODO()
        }

        @PublishedApi
        internal actual fun getOrThrow(): Any? {
            TODO()
        }
    }
