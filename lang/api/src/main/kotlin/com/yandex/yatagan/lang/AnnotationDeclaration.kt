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
 * An annotation class declaration.
 */
public interface AnnotationDeclaration : Annotated, LexicalScope {
    /**
     * Represents an annotation class' property/@interface's method.
     */
    public interface Attribute {
        /**
         * Name of the attribute.
         */
        public val name: String

        /**
         * Type of the attribute.
         */
        public val type: Type
    }

    /**
     * Checks whether the annotation class is given JVM type.
     *
     * @param clazz Java class to check
     */
    public fun isClass(clazz: Class<out kotlin.Annotation>): Boolean {
        return clazz.canonicalName == qualifiedName
    }

    /**
     * Qualified name of the annotation class.
     */
    public val qualifiedName: String

    /**
     * Attributes (annotation class' properties for Kotlin and @interface's methods for Java).
     */
    public val attributes: Sequence<Attribute>

    /**
     * Obtains framework annotation of the given kind.
     *
     * @return the annotation model or `null` if no such annotation is present.
     */
    @Internal
    public fun <T : BuiltinAnnotation.OnAnnotationClass> getAnnotation(
        builtinAnnotation: BuiltinAnnotation.Target.OnAnnotationClass<T>
    ): T?

    /**
     * Computes annotation retention.
     *
     * @return annotation retention in Kotlin terms.
     *
     * @see java.lang.annotation.Retention
     * @see kotlin.annotation.Retention
     */
    public fun getRetention(): AnnotationRetention
}