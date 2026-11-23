package kotlin.collections

/**
 * A hash table implementation of [MutableSet].
 *
 * This class stores unique elements using a hash table data structure that provides fast lookups
 * and ensures no duplicate elements are stored. It fully implements the [MutableSet] contract,
 * providing all standard Set operations including lookup, insertion, and removal.
 *
 * ## Null elements
 *
 * [HashSet] accepts `null` as an element. Since elements are unique, at most one `null` element
 * can exist in the set.
 *
 * ## Element's hash code and equality contracts
 *
 * [HashSet] relies on the [Any.hashCode] and [Any.equals] functions of elements to organize and locate them.
 * Elements are considered equal if their [Any.equals] function returns `true`, and elements that are equal
 * must have the same [Any.hashCode] value. Violating this contract can lead to incorrect behavior, such as
 * duplicate elements being stored or elements becoming unreachable.
 *
 * The [Any.hashCode] and [Any.equals] functions should be consistent and immutable during the lifetime
 * of the element objects. Modifying an element in a way that changes its hash code or equality
 * after it has been added to a [HashSet] may lead to the element becoming unreachable.
 *
 * ## Performance characteristics
 *
 * The performance characteristics below assume that the [Any.hashCode] function of elements distributes
 * them uniformly across the hash table, minimizing collisions. A poor hash function that causes
 * many collisions can degrade performance.
 *
 * [HashSet] provides efficient implementation for common operations:
 *
 * - **Lookup** ([contains]): O(1) time
 * - **Insertion and removal** ([add], [remove]): O(1) time
 * - **Iteration**: O(n) time
 *
 * ## Iteration order
 *
 * [HashSet] does not guarantee any particular order for iteration over its elements.
 * The iteration order is unpredictable and may change when the set is rehashed (when elements are
 * added or removed and the internal capacity is adjusted). Do not rely on any specific iteration order.
 *
 * If a predictable iteration order is required, consider using [LinkedHashSet], which maintains
 * insertion order.
 *
 * ## Usage guidelines
 *
 * [HashSet] uses an internal data structure with a finite *capacity* - the maximum number of elements
 * it can store before needing to grow. As elements are added, the set tracks its *load factor*, which is
 * the ratio of the number of elements to the current capacity. When this ratio exceeds a certain threshold,
 * the set automatically increases its capacity and performs *rehashing* - rebuilding the internal data
 * structure to redistribute elements. Rehashing is a relatively expensive operation that temporarily impacts
 * performance. When creating a [HashSet], you can optionally provide values for the initial capacity and
 * load factor threshold. Note that these parameters are just hints for the implementation and can be ignored.
 *
 * To optimize performance and memory usage:
 *
 * - If the number of elements is known in advance, use the constructor with initial capacity
 *   to avoid multiple rehashing operations as the set grows.
 * - Choose an appropriate load factor when creating the set. A lower load factor reduces collision
 *   probability but uses more memory, while a higher load factor saves memory but may increase
 *   lookup time. The default load factor typically provides a good balance.
 * - Ensure element objects have well-distributed [Any.hashCode] implementations to minimize collisions
 *   and maintain good performance.
 * - Prefer [addAll] over multiple individual [add] calls when adding multiple elements.
 *
 * ## Thread safety
 *
 * [HashSet] is not thread-safe. If multiple threads access an instance concurrently and at least
 * one thread modifies it, external synchronization is required.
 *
 * @param E the type of elements contained in the set. The mutable set is invariant in its element type.
 */
actual open class HashSet<E> : AbstractMutableSet<E>, MutableSet<E> {
    private companion object {
        private val PRESENT = Any()
        private val Empty = HashSet<Nothing>(0).also { it.backing.markReadOnly() }
    }

    private val backing: HashMap<E, Any?>

    internal constructor(backing: HashMap<E, Any?>) : super() {
        this.backing = backing
    }

    /**
     * Creates a new empty [HashSet] with the specified initial capacity.
     *
     * Capacity is the maximum number of elements the set is able to store in the current internal data structure.
     * When the set gets full by a certain default load factor, its capacity is expanded,
     * which usually leads to rebuild of the internal data structure.
     *
     * @param initialCapacity the initial capacity of the created set.
     *   Note that the argument is just a hint for the implementation and can be ignored.
     *
     * @throws IllegalArgumentException if [initialCapacity] is negative.
     */
    public actual constructor(initialCapacity: Int) : this(HashMap(initialCapacity))

    /**
     * Creates a new empty [HashSet] with the specified initial capacity and load factor.
     *
     * Capacity is the maximum number of elements the set is able to store in the current internal data structure.
     * Load factor is the measure of how full the set is allowed to get in relation to
     * its capacity before the capacity is expanded, which usually leads to rebuild of the internal data structure.
     *
     * @param initialCapacity the initial capacity of the created set.
     *   Note that the argument is just a hint for the implementation and can be ignored.
     * @param loadFactor the load factor of the created set.
     *   Note that the argument is just a hint for the implementation and can be ignored.
     *
     * @throws IllegalArgumentException if [initialCapacity] is negative or [loadFactor] is non-positive.
     */
    public actual constructor(initialCapacity: Int, loadFactor: Float) : this(HashMap(initialCapacity, loadFactor))

    /**
     * Creates a new [HashSet] filled with the elements of the specified collection.
     */
    public actual constructor(elements: Collection<E>) : this(HashMap(mapCapacity(elements.size))) {
        addAll(elements)
    }

    actual constructor() : this(HashMap())

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun build(): Set<E> {
        backing.markReadOnly()
        return if (backing.isEmpty()) Empty as Set<E> else this
    }

    internal override fun checkIsMutable() {
        backing.checkIsMutable()
    }

    actual override val size: Int
        get() = backing.size

    actual override fun isEmpty(): Boolean = backing.isEmpty()

    actual override fun contains(element: E): Boolean = backing.containsKey(element)

    @IgnorableReturnValue
    actual override fun add(element: E): Boolean = backing.put(element, PRESENT) == null

    @IgnorableReturnValue
    actual override fun remove(element: E): Boolean = backing.remove(element) === PRESENT

    actual override fun clear() {
        backing.clear()
    }

    actual override fun iterator(): MutableIterator<E> = backing.keys.iterator()
}