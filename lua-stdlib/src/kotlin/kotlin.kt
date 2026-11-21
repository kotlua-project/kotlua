package kotlin

/**
 * Returns an empty array of the specified type [T].
 */
public actual inline fun <T> emptyArray(): Array<T> = Array<Any>(0).unsafeCast<Array<T>>()
