/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.rt.engine

import com.yandex.yatagan.lang.rt.MethodSignatureEquivalenceWrapper
import com.yandex.yatagan.lang.rt.boxed
import com.yandex.yatagan.lang.rt.signatureEquivalence
import com.yandex.yatagan.rt.support.DynamicValidationDelegate
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method


internal open class InvocationHandlerBase(
    protected val validationPromise: DynamicValidationDelegate.Promise?,
) : InvocationHandler {
    private val handlers = hashMapOf<MethodSignatureEquivalenceWrapper, MethodHandler>()

    interface MethodHandler {
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
        validationPromise.awaitOnError {
            return handler(proxy, args).also { result ->
                val returnType = method.returnType
                if (returnType !== Void.TYPE) {
                    if (result == null) {
                        check(!returnType.isPrimitive) {
                            "Can't return `null` from a method with a primitive return type: $returnType"
                        }
                    } else {
                        check(returnType.boxed().isAssignableFrom(result.javaClass)) {
                            "Expected ${returnType}, got ${result.javaClass}"
                        }
                    }
                }
            }
        }
    }

    private class HashCodeHandler : MethodHandler {
        override fun invoke(proxy: Any, args: Array<Any?>?): Int = System.identityHashCode(proxy)
    }

    private class EqualsHandler : MethodHandler {
        override fun invoke(proxy: Any, args: Array<Any?>?): Boolean {
            val (other) = args!!
            return proxy === other
        }
    }

    private inner class ToStringHandler : MethodHandler {
        override fun invoke(proxy: Any, args: Array<Any?>?): String = this@InvocationHandlerBase.toString()
    }
}
