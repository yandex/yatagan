package com.yandex.yatagan.core.graph

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
     * Associates the key with the value.
     * The value can only be associated once to prevent strange mutability-driven errors.
     *
     * @param key the typed key
     * @param value the value to associate with the key
     * @throws IllegalStateException if there's already a value associated with the key
     */
    public operator fun <V : Any> set(key: Key<V>, value: V)
}