/*
 * Copyright 2023 Yandex LLC
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

package com.yandex.yatagan.base.api

/**
 * An extension API, which allows an object to have associated data, accessible by strictly-typed keys.
 *
 * Eliminates the need to manage external mappings.
 *
 * Implementations are not required to be thread-safe.
 */
public interface Extensible {
    public interface Key<V : Any> {
        public val keyType: Class<V>
    }

    /**
     * Obtains previously set value for the key
     *
     * @param key the typed key
     * @return the associated value
     * @throws IllegalStateException if there's no value for the key present
     */
    public operator fun <V : Any> get(key: Key<V>): V

    /**
     * Obtains previously set value for the key, if any available
     *
     * @param key the typed key
     * @return the associated value if available, `null` otherwise.
     */
    public fun <V : Any> getOrNull(key: Key<V>): V?

    /**
     * Associates the key with the value.
     * The value can only be associated once to prevent strange mutability-driven errors.
     *
     * @param key the typed key
     * @param value the value to associate with the key
     * @throws IllegalStateException if there's already a value associated with the key
     */
    public operator fun <V : Any> set(key: Key<V>, value: V)
}