package com.yandex.daggerlite

/**
 * This is only needed to prohibit Java-specific api usage from Kotlin.
 */
@RequiresOptIn(
    "This api is designed specifically for Java clients. Use inline variants with functional types in Kotlin."
)
internal annotation class JavaApi

@JavaApi
fun interface Consumer<in T> {
    fun accept(value: T)
}

@JavaApi
fun interface Function<in T, out R> {
    fun apply(value: T): R
}
