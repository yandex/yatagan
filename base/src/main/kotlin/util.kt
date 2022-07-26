package com.yandex.daggerlite.base

import java.util.ServiceLoader
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

inline fun <R> ifOrElseNull(condition: Boolean, block: () -> R): R? {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return if (condition) block() else null
}

inline fun <reified S : Any> loadServices(): List<S> {
    val serviceClass = S::class.java
    return ServiceLoader.load(serviceClass, serviceClass.classLoader).toList()
}

inline fun <T, R> Iterable<T>.zipWithNextOrNull(block: (T, T?) -> R): List<R> {
    contract { callsInPlace(block) }

    val iterator = iterator()
    if (!iterator.hasNext()) {
        return emptyList()
    }

    val iteratorNext = iterator().also { it.next() }

    val list: MutableList<R> = when (this) {
        is Collection<*> -> ArrayList(size - 1)
        else -> ArrayList()
    }
    while (iterator.hasNext()) {
        val first = iterator.next()
        val second = ifOrElseNull(iterator.hasNext()) { iteratorNext.next() }
        list.add(block(first, second))
    }
    return list
}