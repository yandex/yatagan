package com.yandex.daggerlite.base

/**
 * The same as [ObjectCache] yet supports a composite key with two parts.
 *
 * @See ObjectCache
 */
@Suppress("LeakingThis")
abstract class BiObjectCache<K1, K2, V : Any> : ObjectCacheBase() {
    @PublishedApi
    internal val cache = hashMapOf<K1, MutableMap<K2, V>>()

    init {
        ObjectCacheRegistry.register(this)
    }

    inline fun createCached(key1: K1, key2: K2, block: (K1, K2) -> V): V {
        return cache.getOrPut(key1, ::hashMapOf).getOrPut(key2) { block(key1, key2) }
    }

    override fun clear() {
        cache.clear()
    }
}