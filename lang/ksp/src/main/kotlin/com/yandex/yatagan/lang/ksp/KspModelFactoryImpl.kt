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

package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.Variance
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.AnnotationDeclaration
import com.yandex.yatagan.lang.AnnotationValueFactory
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.TypeDeclarationKind
import com.yandex.yatagan.lang.compiled.CtLangModelFactoryBase

class KspModelFactoryImpl : CtLangModelFactoryBase(), AnnotationValueFactory {
    private val listDeclaration by lazy(LazyThreadSafetyMode.PUBLICATION) {
        checkNotNull(Utils.resolver.getClassDeclarationByName("java.util.List")) {
            "FATAL: Unable to define `java.util.List`, check classpath"
        }
    }
    private val setDeclaration by lazy(LazyThreadSafetyMode.PUBLICATION) {
        checkNotNull(Utils.resolver.getClassDeclarationByName("java.util.Set")) {
            "FATAL: Unable to define `java.util.Set`, check classpath"
        }
    }
    private val mapDeclaration by lazy(LazyThreadSafetyMode.PUBLICATION) {
        checkNotNull(Utils.resolver.getClassDeclarationByName("java.util.Map")) {
            "FATAL: Unable to define `java.util.Map`, check classpath"
        }
    }
    private val collectionDeclaration by lazy(LazyThreadSafetyMode.PUBLICATION) {
        checkNotNull(Utils.resolver.getClassDeclarationByName("java.util.Collection")) {
            "FATAL: Unable to define `java.util.Collection`, check classpath"
        }
    }
    private val providerDeclaration by lazy(LazyThreadSafetyMode.PUBLICATION) {
        checkNotNull(Utils.resolver.getClassDeclarationByName("javax.inject.Provider")) {
            "FATAL: unable to define `javax.inject.Provider` declaration, ensure Yatagan API is present on the classpath"
        }
    }

    override fun getParameterizedType(
        type: LangModelFactory.ParameterizedType,
        parameter: Type,
        isCovariant: Boolean,
    ): Type {
        if (parameter !is KspTypeImpl) {
            return super.getParameterizedType(type, parameter, isCovariant)
        }
        val declaration = when (type) {
            LangModelFactory.ParameterizedType.List -> listDeclaration
            LangModelFactory.ParameterizedType.Set -> setDeclaration
            LangModelFactory.ParameterizedType.Collection -> collectionDeclaration
            LangModelFactory.ParameterizedType.Provider -> providerDeclaration
        }
        with(Utils.resolver) {
            val argument = getTypeArgument(
                typeRef = parameter.impl.asReference(),
                variance = if (isCovariant) Variance.COVARIANT else Variance.INVARIANT,
            )
            return KspTypeImpl(
                reference = declaration.asType(listOf(argument)).asReference(),
                typePosition = TypeMapCache.Position.Parameter,
            )
        }
    }

    override fun getMapType(keyType: Type, valueType: Type, isCovariant: Boolean): Type {
        if (keyType !is KspTypeImpl || valueType !is KspTypeImpl) {
            return super.getMapType(keyType, valueType, isCovariant)
        }
        with(Utils.resolver) {
            val keyTypeArgument = getTypeArgument(
                typeRef = keyType.impl.asReference(),
                variance = Variance.INVARIANT,
            )
            val valueTypeArgument = getTypeArgument(
                typeRef = valueType.impl.asReference(),
                variance = if (isCovariant) Variance.COVARIANT else Variance.INVARIANT,
            )
            return KspTypeImpl(
                reference = mapDeclaration.asType(listOf(keyTypeArgument, valueTypeArgument)).asReference(),
                typePosition = TypeMapCache.Position.Parameter,
            )
        }
    }

    override fun getTypeDeclaration(
        packageName: String,
        simpleName: String,
        vararg simpleNames: String
    ): TypeDeclaration? {
        val qualifiedName = buildString {
            if (packageName.isNotEmpty()) {
                append(packageName).append('.')
            }
            append(simpleName)
            for (name in simpleNames) append('.').append(name)
        }
        val declaration = Utils.resolver.getClassDeclarationByName(qualifiedName) ?: return null
        if (declaration.classKind == ClassKind.ENUM_ENTRY) {
            // Explicitly prohibit directly getting enum entries to get consistent behavior.
            return null
        }
        return KspTypeImpl(declaration.asType(emptyList())).declaration
    }

    override fun getAnnotation(
        declaration: AnnotationDeclaration,
        argumentsSupplier: (AnnotationDeclaration.Attribute) -> Annotation.Value,
    ): Annotation {
        require(declaration is KspAnnotationImpl.AnnotationClassImpl) { "declaration" }
        return KspSyntheticAnnotationImpl(
            annotationClass = declaration,
            values = declaration.attributes.associateWith(argumentsSupplier),
        )
    }

    override val isInRuntimeEnvironment: Boolean
        get() = false

    override val annotationValueFactory: AnnotationValueFactory
        get() = this

    override fun valueOf(value: Boolean): Annotation.Value = KspAnnotationImpl.ValueImpl(value)
    override fun valueOf(value: Byte): Annotation.Value = KspAnnotationImpl.ValueImpl(value)
    override fun valueOf(value: Short): Annotation.Value = KspAnnotationImpl.ValueImpl(value)
    override fun valueOf(value: Int): Annotation.Value = KspAnnotationImpl.ValueImpl(value)
    override fun valueOf(value: Long): Annotation.Value = KspAnnotationImpl.ValueImpl(value)
    override fun valueOf(value: Char): Annotation.Value = KspAnnotationImpl.ValueImpl(value)
    override fun valueOf(value: Float): Annotation.Value = KspAnnotationImpl.ValueImpl(value)
    override fun valueOf(value: Double): Annotation.Value = KspAnnotationImpl.ValueImpl(value)
    override fun valueOf(value: String): Annotation.Value = KspAnnotationImpl.ValueImpl(value)
    override fun valueOf(value: Type): Annotation.Value {
        require(value is KspTypeImpl) { "value" }
        return KspAnnotationImpl.ValueImpl(value.impl)
    }

    override fun valueOf(value: Annotation): Annotation.Value {
        require(value is KspAnnotationBase) { "value" }
        return KspAnnotationImpl.ValueImpl(value)
    }

    override fun valueOf(enum: Type, constant: String): Annotation.Value {
        require(enum.declaration.kind == TypeDeclarationKind.Enum) { "enum" }
        // TODO: Check `constant` belong to the `enum`.
        return KspAnnotationImpl.ValueImpl(enum to constant)
    }

    override fun valueOf(value: List<Annotation.Value>): Annotation.Value {
        require(value.all { it is KspAnnotationImpl.ValueImpl }) { "value" }
        return KspAnnotationImpl.ValueImpl(value.map { it.platformModel })
    }
}
