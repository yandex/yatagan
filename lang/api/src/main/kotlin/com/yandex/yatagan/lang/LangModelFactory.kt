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
import java.util.concurrent.atomic.AtomicReference

/**
 * An interface that provides an API to create `lang`-level model objects.
 */
public interface LangModelFactory {

    public enum class ParameterizedType {
        /**
         * `java.util.List`
         */
        List,

        /**
         * `java.util.Set`
         */
        Set,

        /**
         * `java.util.Collection`
         */
        Collection,

        /**
         * `javax.inject.Provider`
         */
        Provider,
    }

    /**
     * Obtains a map type.
     *
     * @param keyType key type for a map
     * @param valueType value type for a map
     * @param isCovariant `true` if the resulting type needs to be covariant over value type
     * `? extends V`/`out V`, where V - [valueType])
     *
     * @return a `java.util.Map` type, parameterized by the given [keyType] and [valueType].
     */
    public fun getMapType(keyType: Type, valueType: Type, isCovariant: Boolean = false): Type

    /**
     * Obtains a parameterized type.
     *
     * @return the resulting parameterized type
     *
     * @param type one of available parameterized type declarations
     * @param parameter type parameter to use
     * @param isCovariant `true` if the resulting type needs to be covariant
     * (`? extends T`/`out T`, where T - [parameter])
     */
    public fun getParameterizedType(
        type: ParameterizedType,
        parameter: Type,
        isCovariant: Boolean,
    ): Type

    /**
     * Gets a type declaration by the fully qualified name.
     *
     * @return a type declaration model by the given name. If kotlin-platform type is requested, e.g. `kotlin.String`,
     * Java counterpart is returned, e.g. `java.lang.String`. `null` is returned when no such type can be found.
     *
     * @param packageName package name where the class is located.
     * @param simpleName a single simple class name.
     * @param simpleNames multiple names if the class is nested, empty if the class is top-level.
     */
    public fun getTypeDeclaration(
        packageName: String,
        simpleName: String,
        vararg simpleNames: String,
    ): TypeDeclaration?

    /**
     * Constructs an annotation of the given type and with the given parameters.
     * NOTE: In order to obtain a valid [declaration], one may inspect existing annotations using
     * [Annotation.annotationClass] or locate a type by name using [getTypeDeclaration] and then calling
     * [TypeDeclaration.asAnnotationDeclaration] on it.
     *
     * @param declaration annotation type.
     * @param arguments annotation arguments in the order of declaration. The value MUST be specified for each declared
     *  parameter, default values are not honored.
     * @return constructed annotation object.
     * @throws IllegalArgumentException if any argument is of unexpected class or otherwise not applicable.
     */
    public fun getAnnotation(
        declaration: AnnotationDeclaration,
        argumentsSupplier: (AnnotationDeclaration.Attribute) -> Annotation.Value,
    ): Annotation

    /**
     * Creates a synthetic "no"-type which can be used when type object is required but no actual type is present.
     */
    @Internal
    public fun createNoType(name: String): Type

    /**
     * `true` if the code runs in RT mode (using reflection). `false` if codegen mode.
     */
    public val isInRuntimeEnvironment: Boolean

    public val annotationValueFactory: AnnotationValueFactory

    @OptIn(Internal::class)
    public companion object : LangModelFactory {
        @Internal
        public val delegate: AtomicReference<LangModelFactory> = AtomicReference()

        override fun getParameterizedType(
            type: ParameterizedType,
            parameter: Type,
            isCovariant: Boolean,
        ): Type {
            return checkNotNull(delegate.get()).getParameterizedType(type, parameter, isCovariant)
        }

        override fun getMapType(keyType: Type, valueType: Type, isCovariant: Boolean): Type {
            return checkNotNull(delegate.get()).getMapType(keyType, valueType, isCovariant)
        }

        override fun getTypeDeclaration(
            packageName: String,
            simpleName: String,
            vararg simpleNames: String
        ): TypeDeclaration? = checkNotNull(delegate.get()).getTypeDeclaration(packageName, simpleName, *simpleNames)

        override fun getAnnotation(
            declaration: AnnotationDeclaration,
            argumentsSupplier: (AnnotationDeclaration.Attribute) -> Annotation.Value,
        ): Annotation = checkNotNull(delegate.get()).getAnnotation(declaration, argumentsSupplier)

        @Internal
        override fun createNoType(name: String): Type = checkNotNull(delegate.get()).createNoType(name)

        override val isInRuntimeEnvironment: Boolean get() = checkNotNull(delegate.get()).isInRuntimeEnvironment

        override val annotationValueFactory: AnnotationValueFactory
            get() = checkNotNull(delegate.get()).annotationValueFactory
    }
}
