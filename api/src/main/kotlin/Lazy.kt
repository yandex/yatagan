package com.yandex.daggerlite

import javax.inject.Provider

/**
 * A marker extension of [Provider] interface.
 * Implementations must cache their values.
 */
interface Lazy<out T> : Provider<@UnsafeVariance T>
