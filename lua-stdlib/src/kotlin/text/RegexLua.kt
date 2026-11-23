package kotlin.text

public actual enum class RegexOption {
    IGNORE_CASE,
    MULTILINE,
}

public actual class MatchGroup internal constructor(
    public actual val value: String,
)

public actual class Regex public actual constructor(
    public actual val pattern: String,
    public actual val options: Set<RegexOption>,
) {
    public actual constructor(pattern: String) : this(pattern, emptySet())

    public actual constructor(pattern: String, option: RegexOption) : this(pattern, setOf(option))

    public actual fun matchEntire(input: CharSequence): MatchResult? {
        notImplemented()
    }

    public actual infix fun matches(input: CharSequence): Boolean {
        notImplemented()
    }

    @SinceKotlin("1.7")
    public actual fun matchAt(input: CharSequence, index: Int): MatchResult? {
        notImplemented()
    }

    @SinceKotlin("1.7")
    @WasExperimental(ExperimentalStdlibApi::class)
    public actual fun matchesAt(input: CharSequence, index: Int): Boolean {
        notImplemented()
    }

    public actual fun containsMatchIn(input: CharSequence): Boolean {
        notImplemented()
    }

    public actual fun replace(input: CharSequence, replacement: String): String {
        notImplemented()
    }

    public actual fun replace(input: CharSequence, transform: (MatchResult) -> CharSequence): String {
        notImplemented()
    }

    public actual fun replaceFirst(input: CharSequence, replacement: String): String {
        notImplemented()
    }

    public actual fun find(input: CharSequence, startIndex: Int): MatchResult? {
        notImplemented()
    }

    public actual fun findAll(input: CharSequence, startIndex: Int): Sequence<MatchResult> {
        notImplemented()
    }

    public actual fun split(input: CharSequence, limit: Int): List<String> {
        notImplemented()
    }

    @SinceKotlin("1.6")
    public actual fun splitToSequence(input: CharSequence, limit: Int): Sequence<String> {
        notImplemented()
    }

    public actual companion object {
        public actual fun fromLiteral(literal: String): Regex =
            Regex(escape(literal))

        public actual fun escape(literal: String): String {
            notImplemented()
        }

        public actual fun escapeReplacement(literal: String): String {
            notImplemented()
        }
    }
}

/**
 * Returns a named group with the specified [name].
 *
 * @return An instance of [MatchGroup] if the group with the specified [name] was matched or `null` otherwise.
 * @throws IllegalArgumentException if there is no group with the specified [name] defined in the regex pattern.
 * @throws UnsupportedOperationException if this match group collection doesn't support getting match groups by name,
 * for example, when it's not supported by the current platform.
 */
@SinceKotlin("1.9")
public actual operator fun MatchGroupCollection.get(name: String): MatchGroup? {
    notImplemented()
}

private fun notImplemented(): Nothing {
    TODO("Regex is not implemented yet for Lua target")
}
