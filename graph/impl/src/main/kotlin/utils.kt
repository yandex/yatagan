package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.graph.Extensible
import com.yandex.daggerlite.graph.WithParents

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

/**
 * Allows implementing [WithParents] trait by delegating to [Extensible] trait.
 */
internal fun <C, P> hierarchyExtension(
    delegate: P,
    key: Extensible.Key<C>,
): WithParents<C> where C : WithParents<C>, P : WithParents<P>, P : Extensible {
    return object : WithParents<C> {
        override val parent: C?
            get() = delegate.parent?.let { it[key] }
    }
}