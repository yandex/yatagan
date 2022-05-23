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
