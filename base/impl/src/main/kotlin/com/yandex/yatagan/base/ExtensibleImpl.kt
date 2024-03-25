package com.yandex.yatagan.base

import com.yandex.yatagan.base.api.Extensible

open class ExtensibleImpl<E : Extensible<E>> : Extensible<E> {
    private val data = hashMapOf<Extensible.Key<*, E>, Any>()

    override fun <V: Any> get(key: Extensible.Key<V, E>): V {
        val value = checkNotNull(data[key]) {
            "No value present for key $key"
        }
        return key.keyType.cast(value)
    }

    override fun <V : Any> set(key: Extensible.Key<V, E>, value: V) {
        check(key !in data) {
            "Value already present for key $key"
        }
        data[key] = value
    }
}