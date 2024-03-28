package com.yandex.yatagan.base

import com.yandex.yatagan.base.api.Extensible

open class ExtensibleImpl<E : Extensible<E>> : Extensible<E> {
    private val data = hashMapOf<Extensible.Key<*, E>, Any>()

    @Suppress("UNCHECKED_CAST")
    override fun <V: Any> get(key: Extensible.Key<V, E>): V = synchronized(data) {
        val value = checkNotNull(data[key]) {
            "No value present for key $key"
        }
        value as V
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V : Any> getOrPut(key: Extensible.Key<V, E>, provider: () -> V): V = synchronized(data) {
        data.getOrPut(key, provider) as V
    }

    override fun <V : Any> set(key: Extensible.Key<V, E>, value: V): Unit = synchronized(data) {
        check(key !in data) {
            "Value already present for key $key"
        }
        data[key] = value
    }
}