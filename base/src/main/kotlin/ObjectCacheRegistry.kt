package com.yandex.daggerlite.base

import com.yandex.daggerlite.base.ObjectCacheRegistry.close
import java.io.Closeable

/**
 * This is an object cache registry where all existing [ObjectCaches][ObjectCacheBase] can be purged by calling
 * [close] or, preferably, using [use] block.
 *
 * Usage of any [ObjectCaches][ObjectCacheBase] outside of such [use] block can result in severe memory leaks.
 */
object ObjectCacheRegistry : Closeable {
    private val caches = arrayListOf<ObjectCacheBase>()

    internal fun register(cache: ObjectCacheBase) {
        caches += cache
    }

    override fun close() {
        caches.forEach(ObjectCacheBase::clear)
    }
}
