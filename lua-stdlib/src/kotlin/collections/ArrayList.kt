@file:OptIn(LuaIntrinsic::class)

package kotlin.collections

@LuaGlobal
@LuaName("table")
private external object LuaArrayListTable {
    fun concat(list: dynamic): String
}

public actual open class ArrayList<E> private constructor(
    private var storage: dynamic,
    private var _size: Int,
) : AbstractMutableList<E>(), MutableList<E>, RandomAccess {
    private companion object {
        private val Empty = ArrayList<Nothing>(0).also { it.isReadOnly = true }
    }

    private var isReadOnly: Boolean = false

    @PublishedApi
    internal constructor(array: Array<Any?>) : this(array.storage, array.size)

    /**
     * Creates a new empty [ArrayList].
     */
    public actual constructor() : this(luaEmptyTable(), 0)

    /**
     * Creates a new empty [ArrayList] with the specified initial capacity.
     *
     * @throws IllegalArgumentException if [initialCapacity] is negative.
     */
    public actual constructor(initialCapacity: Int) : this(luaEmptyTable(), 0) {
        require(initialCapacity >= 0) { "Negative initial capacity: $initialCapacity" }
    }

    /**
     * Creates a new [ArrayList] filled with the elements of the specified collection.
     */
    public actual constructor(elements: Collection<E>) : this(elements.toTypedArray<Any?>())

    @PublishedApi
    internal fun build(): List<E> {
        checkIsMutable()
        isReadOnly = true
        return if (_size > 0) this else Empty
    }

    /** Does nothing in this ArrayList implementation. */
    public actual fun trimToSize() {}

    /** Does nothing in this ArrayList implementation. */
    public actual fun ensureCapacity(minCapacity: Int) {}

    actual override val size: Int get() = _size

    @Suppress("UNCHECKED_CAST")
    actual override fun get(index: Int): E {
        rangeCheck(index)
        return luaTableGet(storage, index + 1) as E
    }

    @IgnorableReturnValue
    actual override fun set(index: Int, element: E): E {
        checkIsMutable()
        rangeCheck(index)
        @Suppress("UNCHECKED_CAST")
        val old = luaTableGet(storage, index + 1) as E
        luaTableSet(storage, index + 1, element)
        return old
    }

    @IgnorableReturnValue
    actual override fun add(element: E): Boolean {
        checkIsMutable()
        luaTableSet(storage, _size + 1, element)
        _size++
        modCount++
        return true
    }

    actual override fun add(index: Int, element: E) {
        checkIsMutable()
        insertionRangeCheck(index)
        var i = _size
        val target = index + 1
        while (i >= target) {
            luaTableSet(storage, i + 1, luaTableGet(storage, i))
            i--
        }
        luaTableSet(storage, target, element)
        _size++
        modCount++
    }

    @IgnorableReturnValue
    actual override fun addAll(elements: Collection<E>): Boolean {
        checkIsMutable()
        if (elements.isEmpty()) return false
        if (elements === this) {
            val count = _size
            var i = 1
            while (i <= count) {
                luaTableSet(storage, count + i, luaTableGet(storage, i))
                i++
            }
            _size += count
        } else {
            for (element in elements) {
                luaTableSet(storage, _size + 1, element)
                _size++
            }
        }
        modCount++
        return true
    }

    @IgnorableReturnValue
    actual override fun addAll(index: Int, elements: Collection<E>): Boolean {
        checkIsMutable()
        insertionRangeCheck(index)
        if (index == _size) return addAll(elements)
        if (elements.isEmpty()) return false

        val added = elements.toTypedArray<Any?>()
        val addCount = added.size
        var i = _size
        val target = index + 1
        while (i >= target) {
            luaTableSet(storage, i + addCount, luaTableGet(storage, i))
            i--
        }
        var j = 0
        while (j < addCount) {
            luaTableSet(storage, target + j, added[j])
            j++
        }
        _size += addCount
        modCount++
        return true
    }

    @IgnorableReturnValue
    actual override fun removeAt(index: Int): E {
        checkIsMutable()
        rangeCheck(index)
        @Suppress("UNCHECKED_CAST")
        val old = luaTableGet(storage, index + 1) as E
        var i = index + 1
        while (i < _size) {
            luaTableSet(storage, i, luaTableGet(storage, i + 1))
            i++
        }
        luaTableSet(storage, _size, null)
        _size--
        modCount++
        return old
    }

    @IgnorableReturnValue
    actual override fun remove(element: E): Boolean {
        checkIsMutable()
        val index = indexOf(element)
        if (index < 0) return false
        removeAt(index)
        return true
    }

    override fun removeRange(fromIndex: Int, toIndex: Int) {
        checkIsMutable()
        AbstractList.checkRangeIndexes(fromIndex, toIndex, _size)
        val count = toIndex - fromIndex
        if (count > 0) {
            var i = fromIndex + 1
            val newSize = _size - count
            while (i <= newSize) {
                luaTableSet(storage, i, luaTableGet(storage, i + count))
                i++
            }
            var tail = newSize + 1
            while (tail <= _size) {
                luaTableSet(storage, tail, null)
                tail++
            }
            _size = newSize
            modCount++
        } else {
            modCount++
        }
    }

    actual override fun clear() {
        checkIsMutable()
        storage = luaEmptyTable()
        _size = 0
        modCount++
    }

    actual override fun contains(element: E): Boolean = indexOf(element) >= 0

    actual override fun indexOf(element: E): Int {
        var i = 0
        while (i < _size) {
            if (luaTableGet(storage, i + 1) == element) return i
            i++
        }
        return -1
    }

    actual override fun lastIndexOf(element: E): Int {
        var i = _size - 1
        while (i >= 0) {
            if (luaTableGet(storage, i + 1) == element) return i
            i--
        }
        return -1
    }

    override fun toString(): String {
        if (_size == 0) return "[]"
        val parts: dynamic = luaEmptyTable()
        luaTableSet(parts, 1, "[")
        var count = 1
        var i = 0
        while (i < _size) {
            if (i > 0) {
                count++
                luaTableSet(parts, count, ", ")
            }
            val element = luaTableGet(storage, i + 1)
            count++
            luaTableSet(parts, count, if (element === this) "(this Collection)" else element.toString())
            i++
        }
        count++
        luaTableSet(parts, count, "]")
        return LuaArrayListTable.concat(parts)
    }

    internal override fun checkIsMutable() {
        if (isReadOnly) throw UnsupportedOperationException()
    }

    @IgnorableReturnValue
    private fun rangeCheck(index: Int) = index.apply {
        AbstractList.checkElementIndex(index, _size)
    }

    @IgnorableReturnValue
    private fun insertionRangeCheck(index: Int) = index.apply {
        AbstractList.checkPositionIndex(index, _size)
    }
}
