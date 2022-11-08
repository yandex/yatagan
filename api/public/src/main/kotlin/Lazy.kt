package com.yandex.yatagan

import javax.inject.Provider

/**
 * A marker extension of [Provider] interface.
 * Implementations must cache their values.
 *
 * NOTE: This is the *framework type*, that has special treatment in Yatagan graphs.
 * An explicit [provision][Provides] for `Lazy<...>` is ill formed - framework manages lazy instances by itself.
 * The same applies to [Provider] interface.
 *
 * @param T wrapped type. Can't be another *framework type*.
 */
fun interface Lazy<out T> : Provider<@UnsafeVariance T>
