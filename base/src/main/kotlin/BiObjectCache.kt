package com.yandex.yatagan.base

/**
 * The same as [ObjectCache] yet supports a composite key with two parts.
 *
 * The implementation uses a cascade of hash-maps.
 * So, it may be preferable over [ObjectCache<Pair<K1, K2>>][ObjectCache] in a case when `|K1| << |K2|`
 * (quantity of the possible unique values of `K1` is much less than of `K2`).
 * Then the cascade may yield better performance.
 *
 * @See ObjectCache
 */
abstract class BiObjectCache<K1, K2, V : Any> : ObjectCacheBase() {
    protected val cache = hashMapOf<K1, MutableMap<K2, V>>()

    init {
        @Suppress("LeakingThis")
        ObjectCacheRegistry.register(this)
    }

    protected inline fun createCached(key1: K1, key2: K2, block: () -> V): V {
        synchronized(this) {
            return cache.getOrPut(key1, ::hashMapOf).getOrPut(key2) { block() }
        }
    }

    override fun clear() {
        synchronized(this) {
            cache.clear()
        }
    }
}