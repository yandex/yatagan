package com.yandex.daggerlite

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Denotes a holder type, that may have a value, or it may not.
 *
 * [java.util.Optional] is not used because it may not be available on some platforms.
 *
 * NOTE: This is the framework interface, that has special treatment in dagger-lite graphs.
 * An explicit [provision][Provides] for `Optional<...>` is ill formed - framework manages optional instances by itself.
 */
class Optional<out T : Any> private constructor(
    @PublishedApi
    internal val value: T?,
) {
    // region Common API

    /**
     * Stored value access.
     *
     * @return stored value
     * @throws NoSuchElementException if no value is present.
     */
    fun get(): T = value ?: throw NoSuchElementException("No value present")

    /**
     *
     * Stored value nullable access.
     * @return the stored value or `null` if no value present.
     */
    fun orNull(): T? = value

    /**
     * `true` if value is present, `false` otherwise.
     */
    val isPresent: Boolean get() = value != null

    /**
     * Stored value access with alternative.
     *
     * @return stored value if present. Otherwise, returns [alternative].
     */
    fun orElse(alternative: @UnsafeVariance T): T = value ?: alternative

    // endregion Common API

    // region Java API

    fun interface Consumer<in T> {
        fun accept(value: T)
    }

    fun interface Function<in T, out R> {
        fun apply(value: T): R
    }

    /**
     * See [ifPresent].
     */
    @JvmName("ifPresent")
    fun ifPresentJava(consumer: Consumer<T>) {
        ifPresent(consumer::accept)
    }

    /**
     * See [ifPresentOrElse].
     */
    @JvmName("ifPresentOrElse")
    fun ifPresentOrElseJava(consumer: Consumer<T>, onEmpty: Runnable) {
        ifPresentOrElse(consumer::accept, onEmpty::run)
    }

    /**
     * See [map].
     */
    @JvmName("map")
    fun <U : Any> mapJava(mapper: Function<T, U?>): Optional<U> = map(mapper::apply)

    // endregion Java API

    // region Kotlin API

    /**
     * Runs [consumer] function with the value *if the value is present*.
     */
    @JvmSynthetic
    inline fun ifPresent(consumer: (T) -> Unit) {
        contract { callsInPlace(consumer, InvocationKind.AT_MOST_ONCE) }
        value?.let(consumer)
    }

    /**
     * Runs [consumer] function with the value *if the value is present*. Otherwise runs [onEmpty].
     */
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

    /**
     * Produces another [Optional] holder with the given [mapper] function.
     * If there's no value, [Optional.empty] is returned.
     */
    @JvmSynthetic
    inline fun <U : Any> map(mapper: (T) -> (U?)): Optional<U> {
        contract { callsInPlace(mapper, InvocationKind.AT_MOST_ONCE) }
        return if (value != null) ofNullable(mapper(value)) else empty()
    }

    // endregion Kotlin API

    companion object {
        /**
         * Creates optional holder for a ready instance.
         */
        @JvmStatic
        fun <T : Any> of(value: T) = Optional(value)

        /**
         * Returns "empty" holder instance.
         */
        @JvmStatic
        fun <T : Any> empty(): Optional<T> = Empty

        /**
         * Creates optional holder with the given value, if it is not `null`. Otherwise [Optional.empty] is returned.
         */
        @JvmStatic
        fun <T : Any> ofNullable(value: T?): Optional<T> = value?.let(::Optional) ?: Empty

        @JvmStatic
        private val Empty: Optional<Nothing> = Optional(null)
    }
}
