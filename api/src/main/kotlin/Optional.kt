package com.yandex.daggerlite

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * TODO: doc.
 */
class Optional<out T : Any> private constructor(
    @PublishedApi
    internal val value: T?,
) {
    // region Common API

    fun get(): T = value ?: throw NoSuchElementException("No value present")

    fun orNull(): T? = value

    val isPresent: Boolean get() = value != null

    fun orElse(alternative: @UnsafeVariance T): T = value ?: alternative

    // endregion Common API

    // region Java API

    @JavaApi
    fun ifPresent(consumer: Consumer<T>) {
        ifPresent(consumer::accept)
    }

    @JavaApi
    fun ifPresentOrElse(consumer: Consumer<T>, onEmpty: Runnable) {
        ifPresentOrElse(consumer::accept, onEmpty::run)
    }

    @JavaApi
    fun <U : Any> map(mapper: Function<T, U?>): Optional<U> = map(mapper::apply)

    // endregion Java API

    // region Kotlin API

    @JvmSynthetic
    inline fun ifPresent(consumer: (T) -> Unit) {
        contract { callsInPlace(consumer, InvocationKind.AT_MOST_ONCE) }
        value?.let(consumer)
    }

    @JvmSynthetic
    inline fun ifPresentOrElse(consumer: (T) -> Unit, onEmpty: () -> Unit) {
        contract {
            callsInPlace(consumer, InvocationKind.AT_MOST_ONCE)
            callsInPlace(onEmpty, InvocationKind.AT_MOST_ONCE)
        }
        if (value != null) {
            consumer(value)
        } else {
            onEmpty()
        }
    }

    @JvmSynthetic
    inline fun <U : Any> map(mapper: (T) -> (U?)): Optional<U> {
        contract { callsInPlace(mapper, InvocationKind.AT_MOST_ONCE) }
        return if (value != null) ofNullable(mapper(value)) else empty()
    }

    // endregion Kotlin API

    companion object {
        @JvmStatic
        fun <T : Any> of(value: T) = Optional(value)

        @JvmStatic
        fun <T : Any> empty(): Optional<T> = Empty

        @JvmStatic
        fun <T : Any> ofNullable(value: T?): Optional<T> = value?.let(::Optional) ?: Empty

        @JvmStatic
        private val Empty: Optional<Nothing> = Optional(null)
    }
}
