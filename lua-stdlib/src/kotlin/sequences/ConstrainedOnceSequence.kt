package kotlin.sequences

internal actual class ConstrainedOnceSequence<T> actual constructor(sequence: Sequence<T>) : Sequence<T> {

    private var sequenceRef: Sequence<T>? = sequence

    actual override fun iterator(): Iterator<T> {
        val sequence = sequenceRef ?: throw IllegalStateException("This sequence can be consumed only once.")
        sequenceRef = null
        return sequence.iterator()
    }
}
