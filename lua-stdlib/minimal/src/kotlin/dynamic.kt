package kotlin

@kotlin.internal.InlineOnly
public inline fun Any?.asDynamic(): dynamic = this

@kotlin.internal.InlineOnly
public inline fun <T> Any?.unsafeCast(): @kotlin.internal.NoInfer T = this.asDynamic()