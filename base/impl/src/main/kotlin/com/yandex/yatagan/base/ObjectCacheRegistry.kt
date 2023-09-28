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

import com.yandex.yatagan.base.ObjectCacheRegistry.close
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
