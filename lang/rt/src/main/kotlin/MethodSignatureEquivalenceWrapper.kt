package com.yandex.yatagan.lang.rt

/**
 * Equivalence wrapper for [java.lang.reflect.Method] using Java language method signature -
 * method name + parameters erasure.
 */
class MethodSignatureEquivalenceWrapper(
    private val method: ReflectMethod,
) {
    private val parameterTypes = method.parameterTypes
    private val cachedHash by lazy {
        parameterTypes.fold(method.name.hashCode()) { hash, type -> 31 * hash + type.hashCode() }
    }

    override fun hashCode(): Int = cachedHash

    override fun equals(other: Any?): Boolean {
        return this === other || (other is MethodSignatureEquivalenceWrapper
                && method.name == other.method.name &&
                parameterTypes.contentEquals(other.parameterTypes))
    }
}