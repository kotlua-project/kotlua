package kotlin.io.encoding

@SinceKotlin("1.8")
internal actual fun Base64.platformCharsToBytes(
    source: CharSequence,
    startIndex: Int,
    endIndex: Int,
): ByteArray = charsToBytesImpl(source, startIndex, endIndex)

@SinceKotlin("1.8")
internal actual fun Base64.platformEncodeToString(
    source: ByteArray,
    startIndex: Int,
    endIndex: Int,
): String = bytesToStringImpl(encodeToByteArrayImpl(source, startIndex, endIndex))

@SinceKotlin("1.8")
internal actual fun Base64.platformEncodeIntoByteArray(
    source: ByteArray,
    destination: ByteArray,
    destinationOffset: Int,
    startIndex: Int,
    endIndex: Int,
): Int = encodeIntoByteArrayImpl(source, destination, destinationOffset, startIndex, endIndex)

@SinceKotlin("1.8")
internal actual fun Base64.platformEncodeToByteArray(
    source: ByteArray,
    startIndex: Int,
    endIndex: Int,
): ByteArray = encodeToByteArrayImpl(source, startIndex, endIndex)
