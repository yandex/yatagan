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

package com.yandex.yatagan.base

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
abstract class ObjectCache<K, V> : ObjectCacheBase() {
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