/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Denotes a holder type, that may have a value, or it may not.
 *
 * `java.util.Optional` is not used because it may not be available on some platforms.
 *
 * NOTE: This is the framework interface, that has special treatment in Yatagan graphs.
 * An explicit [provision][Provides] or any other binding for `Optional<...>` is ill formed -
 * framework manages optional instances by itself.
 */
public class Optional<out T : Any> private constructor(
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
    public fun get(): T = value ?: throw NoSuchElementException("No value present")

    /**
     *
     * Stored value nullable access.
     * @return the stored value or `null` if no value present.
     */
    public fun orNull(): T? = value

    /**
     * `true` if value is present, `false` otherwise.
     */
    public val isPresent: Boolean get() = value != null

    /**
     * Stored value access with alternative.
     *
     * @return stored value if present. Otherwise, returns [alternative].
     */
    public fun orElse(alternative: @UnsafeVariance T): T = value ?: alternative

    // endregion Common API

    // region Java API

    /**
     * A function consumer interface designed to be used with [Optional] in Java code.
     */
    public fun interface Consumer<in T> {
        public fun accept(value: T)
    }

    /**
     * A function interface designed to be used with [Optional] in Java code.
     */
    public fun interface Function<in T, out R> {
        public fun apply(value: T): R
    }

    /**
     * See [ifPresent].
     */
    @JvmName("ifPresent")
    public fun ifPresentJava(consumer: Consumer<T>) {
        ifPresent(consumer::accept)
    }

    /**
     * See [ifPresentOrElse].
     */
    @JvmName("ifPresentOrElse")
    public fun ifPresentOrElseJava(consumer: Consumer<T>, onEmpty: Runnable) {
        ifPresentOrElse(consumer::accept, onEmpty::run)
    }

    /**
     * See [map].
     */
    @JvmName("map")
    public fun <U : Any> mapJava(mapper: Function<T, U?>): Optional<U> = map(mapper::apply)

    // endregion Java API

    // region Kotlin API

    /**
     * Runs [consumer] function with the value *if the value is present*.
     */
    @JvmSynthetic
    public inline fun ifPresent(consumer: (T) -> Unit) {
        contract { callsInPlace(consumer, InvocationKind.AT_MOST_ONCE) }
        value?.let(consumer)
    }

    /**
     * Runs [consumer] function with the value *if the value is present*. Otherwise runs [onEmpty].
     */
    @JvmSynthetic
    public inline fun ifPresentOrElse(consumer: (T) -> Unit, onEmpty: () -> Unit) {
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
    public inline fun <U : Any> map(mapper: (T) -> (U?)): Optional<U> {
        contract { callsInPlace(mapper, InvocationKind.AT_MOST_ONCE) }
        return if (value != null) ofNullable(mapper(value)) else empty()
    }

    // endregion Kotlin API

    public companion object {
        /**
         * Creates optional holder for a ready instance.
         */
        @JvmStatic
        public fun <T : Any> of(value: T): Optional<T> = Optional(value)

        /**
         * Returns "empty" holder instance.
         */
        @JvmStatic
        public fun <T : Any> empty(): Optional<T> = Empty

        /**
         * Creates optional holder with the given value, if it is not `null`. Otherwise [Optional.empty] is returned.
         */
        @JvmStatic
        public fun <T : Any> ofNullable(value: T?): Optional<T> = value?.let(::Optional) ?: Empty

        @JvmStatic
        private val Empty: Optional<Nothing> = Optional(null)
    }
}
