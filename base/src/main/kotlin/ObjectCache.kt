package com.yandex.daggerlite.base

/**
 * A single-key object cache. Does just that - caches an object by a single key.
 *
 * Very useful as a base for a `companion object Factory` when implementing a model which wraps a model from a lower
 * abstraction level and there is a strict one-to-one relation.
 *
 * This cache is thread-safe.
 *
 * To invalidate all used caches - use [ObjectCacheRegistry.close].
 *
 * @see ObjectCacheRegistry
 */
abstract class ObjectCache<K, V : Any> : ObjectCacheBase() {
    @PublishedApi
    internal val cache = hashMapOf<K, V>()

    init {
        @Suppress("LeakingThis")
        ObjectCacheRegistry.register(this)
    }

    inline fun createCached(key: K, crossinline block: (K) -> V): V {
        synchronized(this) {
            return cache.getOrPut(key) { block(key) }
        }
    }

    final override fun clear() {
        synchronized(this) {
            cache.clear()
        }
    }
}