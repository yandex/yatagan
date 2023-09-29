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

/**
 * Model a language construct that can be annotated.
 */
public interface Annotated {
    /**
     * All annotations present of the construct.
     */
    public val annotations: Sequence<Annotation>

    /**
     * Checks whether the construct is annotated with an [Annotation] of the given [type].
     *
     * @param type Java annotation class to check
     */
    public fun <A : kotlin.Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return annotations.any { it.annotationClass.isClass(type) }
    }
}