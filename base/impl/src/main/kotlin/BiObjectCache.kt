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