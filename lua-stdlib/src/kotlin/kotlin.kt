package kotlin

/**
 * Returns an empty array of the specified type [T].
 */
public actual inline fun <T> emptyArray(): Array<T> = Array<Any>(0).unsafeCast<Array<T>>()

/**
 * Creates a new instance of the [Lazy] that uses the specified initialization function [initializer]
 * and the default thread-safety mode [LazyThreadSafetyMode.SYNCHRONIZED].
 * The lock used is both platform- and implementation- specific detail.
 *
 * If the initialization of a value throws an exception, it will attempt to reinitialize the value at next access.
 *
 * @sample samples.lazy.LazySamples.lazySample
 */
public actual fun <T> lazy(initializer: () -> T): Lazy<T> = UnsafeLazyImpl(initializer)

/**
 * Creates a new instance of the [Lazy] that uses the specified initialization function [initializer]
 * and thread-safety [mode].
 *
 * If the initialization of a value throws an exception, it will attempt to reinitialize the value at next access.
 *
 * For [LazyThreadSafetyMode.SYNCHRONIZED], the lock used is both platform- and implementation- specific detail.
 *
 * @sample samples.lazy.LazySamples.lazySynchronizedSample
 * @sample samples.lazy.LazySamples.lazySafePublicationSample
 */
public actual fun <T> lazy(mode: LazyThreadSafetyMode, initializer: () -> T): Lazy<T> = UnsafeLazyImpl(initializer)

/**
 * Creates a new instance of the [Lazy] that uses the specified initialization function [initializer].
 *
 * The [lock] parameter is ignored.
 */
@Deprecated("Synchronization on Any? object is supported only in Kotlin/JVM.", ReplaceWith("lazy(initializer)"))
@DeprecatedSinceKotlin(warningSince = "1.9", errorSince = "2.1")
public actual fun <T> lazy(lock: Any?, initializer: () -> T): Lazy<T> = UnsafeLazyImpl(initializer)
