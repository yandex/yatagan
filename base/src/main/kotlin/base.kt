package com.yandex.daggerlite.base

inline fun <T, K> Sequence<T>.duplicateAwareAssociateBy(
    onDuplicates: (K, Collection<T>) -> Unit,
    keySelector: (T) -> K,
): Map<K, T> {
    return duplicateAwareAssociateByTo(
        destination = LinkedHashMap(),
        keySelector = keySelector,
        onDuplicates = onDuplicates,
    )
}

inline fun <T, K, M : MutableMap<in K, in T>> Sequence<T>.duplicateAwareAssociateByTo(
    destination: M,
    onDuplicates: (K, Collection<T>) -> Unit,
    keySelector: (T) -> K,
): M {
    return groupBy(keySelector).mapValuesTo(destination) { (key: K, values: List<T>) ->
        values.ifContainsDuplicates {
            onDuplicates(key, it)
        }
        values.first()
    }
}

inline fun <T> Collection<T>.ifContainsDuplicates(block: (Set<T>) -> Unit) {
    if (size > 1) {
        val distinct = toSet()
        if (distinct.size > 1) {
            block(distinct)
        }
    }
}
