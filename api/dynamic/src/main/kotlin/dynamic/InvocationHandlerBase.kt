// Copyright 2022 Yandex LLC. All rights reserved.

package com.yandex.daggerlite.dynamic

import com.yandex.daggerlite.lang.rt.MethodSignatureEquivalenceWrapper
import com.yandex.daggerlite.lang.rt.signatureEquivalence
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method


internal open class InvocationHandlerBase : InvocationHandler {
    private val handlers = hashMapOf<MethodSignatureEquivalenceWrapper, MethodHandler>()

    protected interface MethodHandler {
        operator fun invoke(proxy: Any, args: Array<Any?>?): Any?
    }

    init {
        implementMethod(Any::class.java.getMethod("hashCode"), HashCodeHandler())
        implementMethod(Any::class.java.getMethod("equals", Any::class.java), EqualsHandler())
        implementMethod(Any::class.java.getMethod("toString"), ToStringHandler())
    }

    protected fun implementMethod(method: Method, handler: MethodHandler) {
        handlers[method.signatureEquivalence()] = handler
    }

    final override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
        val handler = handlers[method.signatureEquivalence()]
                ?: throw UnsupportedOperationException("Not reached: $method is not handled in proxy")
        return handler(proxy, args)
    }

    private class HashCodeHandler : MethodHandler {
        override fun invoke(proxy: Any, args: Array<Any?>?): Int = System.identityHashCode(proxy)
    }

    private class EqualsHandler : MethodHandler {
        override fun invoke(proxy: Any, args: Array<Any?>?): Boolean {
            val (other) = args!!
            return proxy == other
        }
    }

    private inner class ToStringHandler : MethodHandler {
        override fun invoke(proxy: Any, args: Array<Any?>?): String = this@InvocationHandlerBase.toString()
    }
}
