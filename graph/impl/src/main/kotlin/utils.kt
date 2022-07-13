package com.yandex.daggerlite.graph.impl

internal fun <K, V> mergeMultiMapsForDuplicateCheck(
    fromParent: Map<K, List<V>>?,
    current: Map<K, List<V>>,
): Map<K, List<V>> {
    fromParent ?: return current
    return buildMap<K, MutableList<V>> {
        fromParent.forEach { (k, values) ->
            // Do not include inherited duplicates, they should be checked separately.
            put(k, arrayListOf(values.first()))
        }
        current.forEach { (k, values) ->
            val alreadyPresent = get(k)
            if (alreadyPresent != null) {
                alreadyPresent += values
            } else {
                put(k, values.toMutableList())
            }
        }
    }
}
