/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 */

/*
 * Based on GWT AbstractHashMap
 * Copyright 2008 Google Inc.
 */

@file:OptIn(LuaIntrinsic::class)

package kotlin.collections

import kotlin.collections.MutableMap.MutableEntry
import kotlin.lua.LuaIntrinsic
import kotlin.lua.luaEmptyTable
import kotlin.lua.luaTableGet
import kotlin.lua.luaTableSet

/**
 * A hash table implementation of [MutableMap].
 *
 * This class stores key-value pairs using a hash table data structure that provides fast lookups
 * based on keys. It fully implements the [MutableMap] contract, providing all standard map operations
 * including insertion, removal, and lookup of values by key.
 *
 * ## Null keys and values
 *
 * [HashMap] accepts `null` as a key. Since keys are unique, at most one entry with a `null` key
 * can exist in the map. [HashMap] also accepts `null` as a value, and multiple entries can have
 * `null` values.
 *
 * ## Key's hash code and equality contracts
 *
 * [HashMap] relies on the [Any.hashCode] and [Any.equals] functions of keys to organize and locate entries.
 * Keys are considered equal if their [Any.equals] function returns `true`, and keys that are equal must
 * have the same [Any.hashCode] value. Violating this contract can lead to incorrect behavior.
 *
 * The [Any.hashCode] and [Any.equals] functions should be consistent and immutable during the lifetime
 * of the key objects. Modifying a key object in a way that changes its hash code or equality
 * after it has been used as a key in a [HashMap] may lead to the entry becoming unreachable.
 *
 * ## Performance characteristics
 *
 * The performance characteristics below assume that the [Any.hashCode] function of keys distributes
 * them uniformly across the hash table, minimizing collisions. A poor hash function that causes
 * many collisions can degrade performance.
 *
 * [HashMap] provides efficient implementation for common operations:
 *
 * - **Lookup** ([get], [containsKey]): O(1) time
 * - **Insertion and removal** ([put], [remove]): O(1) time
 * - **Value search** ([containsValue]): O(n) time, requires scanning all entries
 * - **Iteration** ([entries], [keys], [values]): O(n) time
 *
 * ## Iteration order
 *
 * [HashMap] does not guarantee any particular order for iteration over its keys, values, or entries.
 * The iteration order is unpredictable and may change when the map is rehashed (when entries are
 * added or removed and the internal capacity is adjusted). Do not rely on any specific iteration order.
 *
 * If a predictable iteration order is required, consider using [LinkedHashMap], which maintains
 * insertion order.
 *
 * ## Usage guidelines
 *
 * [HashMap] uses an internal data structure with a finite *capacity* - the maximum number of entries
 * it can store before needing to grow. When the map becomes full, it automatically increases its capacity
 * and performs *rehashing* - rebuilding the internal data structure to redistribute entries. Rehashing is
 * a relatively expensive operation that temporarily impacts performance. When creating a [HashMap], you can
 * optionally provide an initial capacity value, which will be used to size the internal data structure,
 * potentially avoiding rehashing operations as the map grows.
 *
 * To optimize performance and memory usage:
 *
 * - If the number of entries is known in advance, use the constructor with initial capacity
 *   to avoid multiple rehashing operations as the map grows.
 * - Ensure key objects have well-distributed [Any.hashCode] implementations to minimize collisions
 *   and maintain good performance.
 * - Prefer [putAll] over multiple individual [put] calls when adding multiple entries.
 *
 * ## Thread safety
 *
 * [HashMap] is not thread-safe. If multiple threads access an instance concurrently and at least
 * one thread modifies it, external synchronization is required.
 *
 * @param K the type of map keys. The map is invariant in its key type.
 * @param V the type of map values. The mutable map is invariant in its value type.
 */
// Classes that extend HashMap and implement `build()` (freezing) operation
// have to make sure mutating methods check `checkIsMutable`.

public actual open class HashMap<K, V> : AbstractMutableMap<K, V>, MutableMap<K, V> {
    private companion object {
        private const val DEFAULT_CAPACITY = 16
        private const val DEFAULT_LOAD_FACTOR = 0.75f
        private const val MAX_CAPACITY = 1 shl 30
        private val Empty = HashMap<Nothing, Nothing>(0).also { it.isReadOnly = true }
    }

    private inner class Entry(
        override val key: K,
        override var value: V,
        val hash: Int,
    ) : MutableEntry<K, V> {
        var nextInBucket: Entry? = null
        var prevOrder: Entry? = null
        var nextOrder: Entry? = null

        override fun setValue(newValue: V): V {
            this@HashMap.checkIsMutable()
            val old = value
            value = newValue
            return old
        }

        override fun equals(other: Any?): Boolean {
            if (other !is Map.Entry<*, *>) return false
            return key == other.key && value == other.value
        }

        override fun hashCode(): Int =
            (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)

        override fun toString(): String = "$key=$value"
    }

    private val loadFactor: Float
    private var buckets: dynamic = luaEmptyTable()
    private var bucketCapacity: Int
    private var threshold: Int
    private var head: Entry? = null
    private var tail: Entry? = null
    private var _size: Int = 0
    private var isReadOnly: Boolean = false
    private var entriesView: MutableSet<MutableEntry<K, V>>? = null

    public actual constructor() : this(DEFAULT_CAPACITY, DEFAULT_LOAD_FACTOR)

    public actual constructor(initialCapacity: Int, loadFactor: Float) : super() {
        if (initialCapacity < 0) {
            throw IllegalArgumentException("Negative initial capacity: $initialCapacity")
        }
        if (!(loadFactor > 0f)) {
            throw IllegalArgumentException("Non-positive load factor: $loadFactor")
        }
        this.loadFactor = loadFactor
        val target = if (initialCapacity < 4) 4 else if (initialCapacity > MAX_CAPACITY) MAX_CAPACITY else initialCapacity
        var cap = 4
        while (cap < target) {
            cap = cap shl 1
        }
        this.bucketCapacity = cap
        this.threshold = computeThreshold(cap, loadFactor)
    }

    public actual constructor(initialCapacity: Int) : this(initialCapacity, DEFAULT_LOAD_FACTOR)

    public actual constructor(original: Map<out K, V>) : this(mapCapacity(original.size), DEFAULT_LOAD_FACTOR) {
        for (entry in original.entries) {
            put(entry.key, entry.value)
        }
    }

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun build(): Map<K, V> {
        checkIsMutable()
        isReadOnly = true
        return if (_size > 0) this else Empty as Map<K, V>
    }

    internal fun markReadOnly() {
        checkIsMutable()
        isReadOnly = true
    }

    internal override fun checkIsMutable() {
        if (isReadOnly) throw UnsupportedOperationException()
    }

    private fun computeThreshold(capacity: Int, lf: Float): Int {
        val t = (capacity * lf).toInt()
        return if (t < 1) 1 else t
    }

    private fun hashOf(key: Any?): Int {
        if (key == null) return 0
        val h = key.hashCode()
        return h xor (h ushr 16)
    }

    @Suppress("UNCHECKED_CAST")
    private fun findEntry(key: Any?): Entry? {
        val h = hashOf(key)
        val idx = h and (bucketCapacity - 1)
        var e = luaTableGet(buckets, idx) as? HashMap<K, V>.Entry
        while (e != null) {
            if (e.hash == h && e.key == key) {
                return e
            }
            e = e.nextInBucket
        }
        return null
    }

    private fun rehash() {
        val oldCap = bucketCapacity
        if (oldCap >= MAX_CAPACITY) {
            threshold = Int.MAX_VALUE
            return
        }
        val newCap = oldCap shl 1
        val newMask = newCap - 1
        val newBuckets: dynamic = luaEmptyTable()
        var e = head
        while (e != null) {
            val idx = e.hash and newMask
            e.nextInBucket = luaTableGet(newBuckets, idx) as? HashMap<K, V>.Entry
            luaTableSet(newBuckets, idx, e)
            e = e.nextOrder
        }
        buckets = newBuckets
        bucketCapacity = newCap
        threshold = computeThreshold(newCap, loadFactor)
    }

    private fun removeEntry(entry: Entry) {
        val idx = entry.hash and (bucketCapacity - 1)
        var prevBucket: Entry? = null
        var cur = luaTableGet(buckets, idx) as? HashMap<K, V>.Entry
        while (cur != null) {
            if (cur === entry) {
                if (prevBucket == null) {
                    luaTableSet(buckets, idx, cur.nextInBucket)
                } else {
                    prevBucket.nextInBucket = cur.nextInBucket
                }
                cur.nextInBucket = null
                break
            }
            prevBucket = cur
            cur = cur.nextInBucket
        }

        val prev = entry.prevOrder
        val next = entry.nextOrder
        if (prev == null) {
            head = next
        } else {
            prev.nextOrder = next
        }
        if (next == null) {
            tail = prev
        } else {
            next.prevOrder = prev
        }
        entry.prevOrder = null
        entry.nextOrder = null
        _size--
    }

    actual override val size: Int
        get() = _size

    actual override fun clear() {
        checkIsMutable()
        buckets = luaEmptyTable()
        head = null
        tail = null
        _size = 0
    }

    actual override fun containsKey(key: K): Boolean = findEntry(key) != null

    actual override fun containsValue(value: V): Boolean {
        var e = head
        while (e != null) {
            if (e.value == value) return true
            e = e.nextOrder
        }
        return false
    }

    actual override operator fun get(key: K): V? = findEntry(key)?.value

    @IgnorableReturnValue
    actual override fun put(key: K, value: V): V? {
        checkIsMutable()
        val h = hashOf(key)
        var idx = h and (bucketCapacity - 1)
        var e = luaTableGet(buckets, idx) as? HashMap<K, V>.Entry
        while (e != null) {
            if (e.hash == h && e.key == key) {
                val old = e.value
                e.value = value
                return old
            }
            e = e.nextInBucket
        }

        if (_size >= threshold) {
            rehash()
            idx = h and (bucketCapacity - 1)
        }

        val newEntry = Entry(key, value, h)
        newEntry.nextInBucket = luaTableGet(buckets, idx) as? HashMap<K, V>.Entry
        luaTableSet(buckets, idx, newEntry)

        val t = tail
        if (t == null) {
            head = newEntry
            tail = newEntry
        } else {
            t.nextOrder = newEntry
            newEntry.prevOrder = t
            tail = newEntry
        }
        _size++
        return null
    }

    @IgnorableReturnValue
    actual override fun remove(key: K): V? {
        checkIsMutable()
        val entry = findEntry(key) ?: return null
        val old = entry.value
        removeEntry(entry)
        return old
    }

    actual override fun putAll(from: Map<out K, V>) {
        checkIsMutable()
        for (entry in from.entries) {
            put(entry.key, entry.value)
        }
    }

    override fun createKeysView(): MutableSet<K> = HashMapKeysDefault(this)

    override fun createValuesView(): MutableCollection<V> = HashMapValuesDefault(this)

    actual override val entries: MutableSet<MutableEntry<K, V>>
        get() = entriesView ?: EntrySet().also { entriesView = it }

    private inner class EntrySet : AbstractMutableSet<MutableEntry<K, V>>() {
        override val size: Int get() = _size

        override fun checkIsMutable() = this@HashMap.checkIsMutable()

        override fun add(element: MutableEntry<K, V>): Boolean =
            throw UnsupportedOperationException("Add is not supported on entries")

        override fun clear() = this@HashMap.clear()

        override fun contains(element: MutableEntry<K, V>): Boolean {
            if ((element as Any?) !is Map.Entry<*, *>) return false
            val found = findEntry(element.key) ?: return false
            return found.value == element.value
        }

        override fun remove(element: MutableEntry<K, V>): Boolean {
            checkIsMutable()
            if ((element as Any?) !is Map.Entry<*, *>) return false
            val found = findEntry(element.key) ?: return false
            if (found.value != element.value) return false
            removeEntry(found)
            return true
        }

        override fun iterator(): MutableIterator<MutableEntry<K, V>> =
            object : MutableIterator<MutableEntry<K, V>> {
                private var nextEntry: Entry? = head
                private var lastReturned: Entry? = null

                override fun hasNext(): Boolean = nextEntry != null

                override fun next(): MutableEntry<K, V> {
                    val e = nextEntry ?: throw NoSuchElementException()
                    lastReturned = e
                    nextEntry = e.nextOrder
                    return e
                }

                override fun remove() {
                    this@HashMap.checkIsMutable()
                    val e = lastReturned
                        ?: throw IllegalStateException("Call next() before removing an element from the iterator.")
                    lastReturned = null
                    removeEntry(e)
                }
            }
    }
}
