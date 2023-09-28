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