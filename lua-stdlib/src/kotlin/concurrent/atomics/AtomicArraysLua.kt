@file:OptIn(ExperimentalAtomicApi::class, LuaIntrinsic::class)

package kotlin.concurrent.atomics

import kotlin.internal.InlineOnly
import kotlin.lua.LuaIntrinsic
import kotlin.lua.luaPlus

@SinceKotlin("2.1")
@ExperimentalAtomicApi
public actual class AtomicIntArray private constructor(
    private val array: IntArray,
    @Suppress("UNUSED_PARAMETER") marker: Int,
) {
    public actual constructor(size: Int) : this(IntArray(size), 0)

    public actual constructor(array: IntArray) : this(array, 0)

    public actual val size: Int
        get() = array.size

    public actual fun loadAt(index: Int): Int = array[index]

    public actual fun storeAt(index: Int, newValue: Int) {
        array[index] = newValue
    }

    public actual fun exchangeAt(index: Int, newValue: Int): Int {
        val old = array[index]
        array[index] = newValue
        return old
    }

    public actual fun compareAndSetAt(index: Int, expectedValue: Int, newValue: Int): Boolean {
        if (array[index] == expectedValue) {
            array[index] = newValue
            return true
        }
        return false
    }

    public actual fun compareAndExchangeAt(index: Int, expectedValue: Int, newValue: Int): Int {
        val old = array[index]
        if (old == expectedValue) {
            array[index] = newValue
        }
        return old
    }

    public actual fun fetchAndAddAt(index: Int, delta: Int): Int {
        val old = array[index]
        array[index] = old + delta
        return old
    }

    public actual fun addAndFetchAt(index: Int, delta: Int): Int {
        val next = array[index] + delta
        array[index] = next
        return next
    }

    public actual override fun toString(): String = array.contentToString()
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun AtomicIntArray.updateAt(index: Int, transform: (Int) -> Int) {
    storeAt(index, transform(loadAt(index)))
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun AtomicIntArray.updateAndFetchAt(index: Int, transform: (Int) -> Int): Int {
    val next = transform(loadAt(index))
    storeAt(index, next)
    return next
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun AtomicIntArray.fetchAndUpdateAt(index: Int, transform: (Int) -> Int): Int {
    val old = loadAt(index)
    storeAt(index, transform(old))
    return old
}

@SinceKotlin("2.1")
@ExperimentalAtomicApi
public actual class AtomicLongArray private constructor(
    private val array: LongArray,
    @Suppress("UNUSED_PARAMETER") marker: Int,
) {
    public actual constructor(size: Int) : this(LongArray(size), 0)

    public actual constructor(array: LongArray) : this(array, 0)

    public actual val size: Int
        get() = array.size

    public actual fun loadAt(index: Int): Long = array[index]

    public actual fun storeAt(index: Int, newValue: Long) {
        array[index] = newValue
    }

    public actual fun exchangeAt(index: Int, newValue: Long): Long {
        val old = array[index]
        array[index] = newValue
        return old
    }

    public actual fun compareAndSetAt(index: Int, expectedValue: Long, newValue: Long): Boolean {
        if (array[index] == expectedValue) {
            array[index] = newValue
            return true
        }
        return false
    }

    public actual fun compareAndExchangeAt(index: Int, expectedValue: Long, newValue: Long): Long {
        val old = array[index]
        if (old == expectedValue) {
            array[index] = newValue
        }
        return old
    }

    public actual fun fetchAndAddAt(index: Int, delta: Long): Long {
        val old = array[index]
        array[index] = luaPlus(old, delta).unsafeCast<Long>()
        return old
    }

    public actual fun addAndFetchAt(index: Int, delta: Long): Long {
        val next = luaPlus(array[index], delta).unsafeCast<Long>()
        array[index] = next
        return next
    }

    public actual override fun toString(): String = array.contentToString()
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun AtomicLongArray.updateAt(index: Int, transform: (Long) -> Long) {
    storeAt(index, transform(loadAt(index)))
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun AtomicLongArray.updateAndFetchAt(index: Int, transform: (Long) -> Long): Long {
    val next = transform(loadAt(index))
    storeAt(index, next)
    return next
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun AtomicLongArray.fetchAndUpdateAt(index: Int, transform: (Long) -> Long): Long {
    val old = loadAt(index)
    storeAt(index, transform(old))
    return old
}

@SinceKotlin("2.1")
@ExperimentalAtomicApi
public actual class AtomicArray<T> public actual constructor(
    private val array: Array<T>,
) {
    public actual val size: Int
        get() = array.size

    public actual fun loadAt(index: Int): T = array[index]

    public actual fun storeAt(index: Int, newValue: T) {
        array[index] = newValue
    }

    public actual fun exchangeAt(index: Int, newValue: T): T {
        val old = array[index]
        array[index] = newValue
        return old
    }

    public actual fun compareAndSetAt(index: Int, expectedValue: T, newValue: T): Boolean {
        if (array[index] === expectedValue) {
            array[index] = newValue
            return true
        }
        return false
    }

    public actual fun compareAndExchangeAt(index: Int, expectedValue: T, newValue: T): T {
        val old = array[index]
        if (old === expectedValue) {
            array[index] = newValue
        }
        return old
    }

    public actual override fun toString(): String = array.contentToString()
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun <T> AtomicArray<T>.updateAt(index: Int, transform: (T) -> T) {
    storeAt(index, transform(loadAt(index)))
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun <T> AtomicArray<T>.updateAndFetchAt(index: Int, transform: (T) -> T): T {
    val next = transform(loadAt(index))
    storeAt(index, next)
    return next
}

@SinceKotlin("2.2")
@ExperimentalAtomicApi
@InlineOnly
public actual inline fun <T> AtomicArray<T>.fetchAndUpdateAt(index: Int, transform: (T) -> T): T {
    val old = loadAt(index)
    storeAt(index, transform(old))
    return old
}
