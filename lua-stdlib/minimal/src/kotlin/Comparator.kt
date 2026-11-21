package kotlin

/**
 * Provides a comparison function for imposing a total ordering between instances of the type [T].
 */
actual fun interface Comparator<T> {
    /**
     * Compares its two arguments for order. Returns zero if the arguments are equal,
     * a negative number if the first argument is less than the second, or a positive number
     * if the first argument is greater than the second.
     */
    public actual fun compare(a: T, b: T): Int
}