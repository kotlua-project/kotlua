@file:Suppress("NON_ABSTRACT_FUNCTION_WITH_NO_BODY", "MUST_BE_INITIALIZED_OR_BE_ABSTRACT")

package kotlin

/**
 * The common base class of all enum classes.
 * See the [Kotlin language documentation](https://kotlinlang.org/docs/reference/enum-classes.html) for more
 * information on enum classes.
 */
public actual abstract class Enum<E : Enum<E>> actual constructor(name: String, ordinal: Int): Comparable<E> {
    public actual companion object {}

    /**
     * Returns the name of this enum constant, exactly as declared in its enum declaration.
     */
    @kotlin.internal.IntrinsicConstEvaluation
    public actual final val name: String

    /**
     * Returns the ordinal of this enumeration constant (its position in its enum declaration, where the initial constant
     * is assigned an ordinal of zero).
     */
    public actual final val ordinal: Int

    public actual override final fun compareTo(other: E): Int

    /**
     * Throws an exception since enum constants cannot be cloned.
     * This method prevents enum classes from inheriting from `Cloneable`.
     */
    protected final fun clone(): Any

    public actual override final fun equals(other: Any?): Boolean

    public actual override final fun hashCode(): Int

    public actual override fun toString(): String
}
