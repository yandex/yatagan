package com.yandex.daggerlite.lang.rt

import java.lang.reflect.Method

/**
 * Equivalence wrapper for [Method] using Java language method signature - method name + parameters erasure.
 */
class MethodSignatureEquivalenceWrapper(
    private val method: Method,
) {
    private val cachedHash by lazy {
        method.parameterTypes.fold(method.name.hashCode()) { hash, type -> 31 * hash + type.hashCode() }
    }

    override fun hashCode(): Int = cachedHash

    override fun equals(other: Any?): Boolean {
        return this === other || (other is MethodSignatureEquivalenceWrapper
                && method.name == other.method.name &&
                method.parameterTypes.contentEquals(other.method.parameterTypes))
    }
}