package kotlin

import kotlin.lua.anyHashCode
import kotlin.lua.luaEq

/**
 * The common base class of all enum classes.
 * See the [Kotlin language documentation](https://kotlinlang.org/docs/reference/enum-classes.html) for more
 * information on enum classes.
 */
@OptIn(LuaIntrinsic::class)
actual abstract class Enum<E : Enum<E>> actual constructor(
    /**
     * Returns the name of this enum constant, exactly as declared in its enum declaration.
     */
    @kotlin.internal.IntrinsicConstEvaluation actual val name: String,
    /**
     * Returns the ordinal of this enumeration constant (its position in its enum declaration, where the initial constant
     * is assigned an ordinal of zero).
     */
    actual val ordinal: Int
): Comparable<E> {
    actual companion object;

    actual final override fun compareTo(other: E): Int =
        if (ordinal < other.ordinal) -1 else if (ordinal > other.ordinal) 1 else 0

    actual final override fun equals(other: Any?): Boolean = luaEq(this, other)

    actual final override fun hashCode(): Int = anyHashCode(this)

    actual override fun toString(): String = name
}
