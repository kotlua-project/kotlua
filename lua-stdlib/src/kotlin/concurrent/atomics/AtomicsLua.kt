@file:OptIn(ExperimentalAtomicApi::class, LuaIntrinsic::class)

package kotlin.concurrent.atomics

import kotlin.internal.InlineOnly
import kotlin.lua.LuaIntrinsic
import kotlin.lua.luaPlus

@SinceKotlin("2.1")
@ExperimentalAtomicApi
public actual class AtomicInt public actual constructor(
    private var value: Int,
) {
    public actual fun load(): Int = value

    public actual fun store(newValue: Int) {
        value = newValue
    }

    public actual fun exchange(newValue: Int): Int {
        val old = value
        value = newValue
        return old
    }

    public actual fun compareAndSet(expectedValue: Int, newValue: Int): Boolean {
        if (value == expectedValue) {
            value = newValue
            return true
        }
        return false
    }

    public actual fun compareAndExchange(expectedValue: Int, newValue: Int): Int {
        val old = value
        if (old == expectedValue) {
            value = newValue
        }
        return old
    }

    public actual fun fetchAndAdd(delta: Int): Int {
        val old = value
        value = old + delta
        return old
    }

    public actual fun addAndFetch(delta: Int): Int {
        val next = value + delta
        value = next
        return next
    }

    public actual override fun toString(): String = value.toString()
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun AtomicInt.update(transform: (Int) -> Int) {
    store(transform(load()))
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun AtomicInt.fetchAndUpdate(transform: (Int) -> Int): Int {
    val old = load()
    store(transform(old))
    return old
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun AtomicInt.updateAndFetch(transform: (Int) -> Int): Int {
    val next = transform(load())
    store(next)
    return next
}

@SinceKotlin("2.1")
@ExperimentalAtomicApi
public actual class AtomicLong public actual constructor(
    private var value: Long,
) {
    public actual fun load(): Long = value

    public actual fun store(newValue: Long) {
        value = newValue
    }

    public actual fun exchange(newValue: Long): Long {
        val old = value
        value = newValue
        return old
    }

    public actual fun compareAndSet(expectedValue: Long, newValue: Long): Boolean {
        if (value == expectedValue) {
            value = newValue
            return true
        }
        return false
    }

    public actual fun compareAndExchange(expectedValue: Long, newValue: Long): Long {
        val old = value
        if (old == expectedValue) {
            value = newValue
        }
        return old
    }

    public actual fun fetchAndAdd(delta: Long): Long {
        val old = value
        value = luaPlus(old, delta).unsafeCast<Long>()
        return old
    }

    public actual fun addAndFetch(delta: Long): Long {
        val next = luaPlus(value, delta).unsafeCast<Long>()
        value = next
        return next
    }

    public actual override fun toString(): String = value.toString()
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun AtomicLong.update(transform: (Long) -> Long) {
    store(transform(load()))
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun AtomicLong.fetchAndUpdate(transform: (Long) -> Long): Long {
    val old = load()
    store(transform(old))
    return old
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun AtomicLong.updateAndFetch(transform: (Long) -> Long): Long {
    val next = transform(load())
    store(next)
    return next
}

@SinceKotlin("2.1")
@ExperimentalAtomicApi
public actual class AtomicBoolean public actual constructor(
    private var value: Boolean,
) {
    public actual fun load(): Boolean = value

    public actual fun store(newValue: Boolean) {
        value = newValue
    }

    public actual fun exchange(newValue: Boolean): Boolean {
        val old = value
        value = newValue
        return old
    }

    public actual fun compareAndSet(expectedValue: Boolean, newValue: Boolean): Boolean {
        if (value == expectedValue) {
            value = newValue
            return true
        }
        return false
    }

    public actual fun compareAndExchange(expectedValue: Boolean, newValue: Boolean): Boolean {
        val old = value
        if (old == expectedValue) {
            value = newValue
        }
        return old
    }

    public actual override fun toString(): String = value.toString()
}

@SinceKotlin("2.1")
@ExperimentalAtomicApi
public actual class AtomicReference<T> public actual constructor(
    private var value: T,
) {
    public actual fun load(): T = value

    public actual fun store(newValue: T) {
        value = newValue
    }

    public actual fun exchange(newValue: T): T {
        val old = value
        value = newValue
        return old
    }

    public actual fun compareAndSet(expectedValue: T, newValue: T): Boolean {
        if (value === expectedValue) {
            value = newValue
            return true
        }
        return false
    }

    public actual fun compareAndExchange(expectedValue: T, newValue: T): T {
        val old = value
        if (old === expectedValue) {
            value = newValue
        }
        return old
    }

    public actual override fun toString(): String = value.toString()
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun <T> AtomicReference<T>.update(transform: (T) -> T) {
    store(transform(load()))
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun <T> AtomicReference<T>.fetchAndUpdate(transform: (T) -> T): T {
    val old = load()
    store(transform(old))
    return old
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun <T> AtomicReference<T>.updateAndFetch(transform: (T) -> T): T {
    val next = transform(load())
    store(next)
    return next
}
