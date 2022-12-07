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

package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.graph.Extensible

internal open class ExtensibleImpl : Extensible {
    private val data = hashMapOf<Extensible.Key<*>, Any>()

    override fun <V: Any> get(key: Extensible.Key<V>): V {
        val value = checkNotNull(data[key]) {
            "No value present for key $key"
        }
        return key.keyType.cast(value)
    }

    override fun <V : Any> set(key: Extensible.Key<V>, value: V) {
        check(key !in data) {
            "Value already present for key $key"
        }
        data[key] = value
    }
}