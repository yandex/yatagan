package com.yandex.daggerlite

import javax.inject.Provider

/**
 * A marker extension of [Provider] interface.
 * Implementations must cache their values.
 *
 * NOTE: This is the framework interface, that has special treatment in dagger-lite graphs.
 * An explicit [provision][Provides] for `Lazy<...>` is ill formed - framework manages lazy instances by itself.
 * The same applies to [Provider] interface.
 */
fun interface Lazy<out T> : Provider<@UnsafeVariance T>
