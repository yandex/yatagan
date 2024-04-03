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

package com.yandex.yatagan.lang

import com.yandex.yatagan.base.api.Internal
import com.yandex.yatagan.lang.scope.LexicalScope

/**
 * Represents a function/method associated with a class from **the Java point of view**.
 * - Constructor is modeled separately by [Constructor].
 * - Top-level kotlin functions are not covered.
 * - Kotlin properties (setters and getters) are also represented by this.
 */
public interface Method : Member, Callable, Comparable<Method>, LexicalScope {
    /**
     * Whether the function is abstract.
     */
    public val isAbstract: Boolean

    /**
     * Return type of the function.
     */
    public val returnType: Type

    /**
     * Obtains framework annotation of the given kind.
     *
     * @return the annotation model or `null` if no such annotation is present.
     */
    @Internal
    public fun <T : BuiltinAnnotation.OnMethod> getAnnotation(
        which: BuiltinAnnotation.Target.OnMethod<T>
    ): T?

    /**
     * Obtains framework annotations of the given class.
     *
     * @return the list of repeatable annotations or an empty list if no such annotations are present.
     */
    @Internal
    public fun <T : BuiltinAnnotation.OnMethodRepeatable> getAnnotations(
        which: BuiltinAnnotation.Target.OnMethodRepeatable<T>
    ): List<T>
}