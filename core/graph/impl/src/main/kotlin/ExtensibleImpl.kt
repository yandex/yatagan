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