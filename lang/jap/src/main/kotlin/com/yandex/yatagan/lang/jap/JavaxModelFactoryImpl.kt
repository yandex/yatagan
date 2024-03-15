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

package com.yandex.yatagan.lang.jap

import com.google.auto.common.SimpleAnnotationMirror
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.AnnotationDeclaration
import com.yandex.yatagan.lang.AnnotationValueFactory
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration
import com.yandex.yatagan.lang.TypeDeclarationKind
import com.yandex.yatagan.lang.common.TypeBase
import com.yandex.yatagan.lang.compiled.CtErrorType
import com.yandex.yatagan.lang.compiled.CtLangModelFactoryBase
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.AnnotationValueVisitor
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind

class JavaxModelFactoryImpl : CtLangModelFactoryBase(), AnnotationValueFactory {
    private val listElement: TypeElement by lazy {
        Utils.elements.getTypeElement("java.util.List")
    }
    private val setElement: TypeElement by lazy {
        Utils.elements.getTypeElement("java.util.Set")
    }
    private val collectionElement: TypeElement by lazy {
        Utils.elements.getTypeElement("java.util.Collection")
    }
    private val mapElement: TypeElement by lazy {
        Utils.elements.getTypeElement("java.util.Map")
    }
    private val providerElement: TypeElement by lazy {
        Utils.elements.getTypeElement("javax.inject.Provider")
    }

    override fun getMapType(keyType: Type, valueType: Type, isCovariant: Boolean): Type {
        if (keyType !is JavaxTypeImpl || valueType !is JavaxTypeImpl) {
            return super.getMapType(keyType, valueType, isCovariant)
        }
        with(Utils.types) {
            val valueArgType =
                if (isCovariant) getWildcardType(/*extends*/ valueType.impl, /*super*/ null)
                else valueType.impl
            return JavaxTypeImpl(getDeclaredType(mapElement, keyType.impl, valueArgType))
        }
    }

    override fun getParameterizedType(
        type: LangModelFactory.ParameterizedType,
        parameter: Type,
        isCovariant: Boolean,
    ): Type {
        if (parameter !is JavaxTypeImpl) {
            return super.getParameterizedType(type, parameter, isCovariant)
        }
        val element = when(type) {
            LangModelFactory.ParameterizedType.List -> listElement
            LangModelFactory.ParameterizedType.Set -> setElement
            LangModelFactory.ParameterizedType.Collection -> collectionElement
            LangModelFactory.ParameterizedType.Provider -> providerElement
        }
        with(Utils.types) {
            val typeImpl = parameter.impl
            val argType = if (isCovariant) getWildcardType(/*extends*/ typeImpl,/*super*/ null) else typeImpl
            return JavaxTypeImpl(getDeclaredType(element, argType))
        }
    }

    override fun getTypeDeclaration(
        packageName: String,
        simpleName: String,
        vararg simpleNames: String
    ): TypeDeclaration? {
        val name = buildString {
            if (packageName.isNotEmpty()) {
                append(packageName).append('.')
            }
            append(simpleName)
            for (name in simpleNames) append('.').append(name)
        }
        val element = Utils.elements.getTypeElement(name) ?: return null
        return JavaxTypeDeclarationImpl(element.asType().asDeclaredType())
    }

    override fun getAnnotation(
        declaration: AnnotationDeclaration,
        argumentsSupplier: (AnnotationDeclaration.Attribute) -> Annotation.Value,
    ): Annotation {
        // TODO: Normal require messages
        require(declaration is JavaxAnnotationImpl.AnnotationClassImpl) { "declaration" }
        return JavaxAnnotationImpl(SimpleAnnotationMirror.of(
            declaration.impl, declaration.attributes.associateBy(
                keySelector = { it.name },
                valueTransform = { attribute ->
                    val value = argumentsSupplier(attribute)
                    require(value is JavaxAnnotationImpl.ValueImpl) { "argumentsSupplier" }
                    value.value
                },
            )
        ))
    }

    override val isInRuntimeEnvironment: Boolean
        get() = false

    override val annotationValueFactory: AnnotationValueFactory
        get() = this

    override fun valueOf(value: Boolean): Annotation.Value = JavaxAnnotationImpl.ValueImpl(object : AnnotationValue {
        override fun getValue() = value
        override fun <R, P> accept(v: AnnotationValueVisitor<R, P>, p: P) = v.visitBoolean(value, p)
    }, Utils.types.getPrimitiveType(TypeKind.BOOLEAN))

    override fun valueOf(value: Byte): Annotation.Value = JavaxAnnotationImpl.ValueImpl(object : AnnotationValue {
        override fun getValue() = value
        override fun <R, P> accept(v: AnnotationValueVisitor<R, P>, p: P) = v.visitByte(value, p)
    }, Utils.types.getPrimitiveType(TypeKind.BYTE))

    override fun valueOf(value: Short): Annotation.Value = JavaxAnnotationImpl.ValueImpl(object : AnnotationValue {
        override fun getValue() = value
        override fun <R, P> accept(v: AnnotationValueVisitor<R, P>, p: P) = v.visitShort(value, p)
    }, Utils.types.getPrimitiveType(TypeKind.SHORT))

    override fun valueOf(value: Int): Annotation.Value = JavaxAnnotationImpl.ValueImpl(object : AnnotationValue {
        override fun getValue() = value
        override fun <R, P> accept(v: AnnotationValueVisitor<R, P>, p: P) = v.visitInt(value, p)
    }, Utils.types.getPrimitiveType(TypeKind.INT))

    override fun valueOf(value: Long): Annotation.Value = JavaxAnnotationImpl.ValueImpl(object : AnnotationValue {
        override fun getValue() = value
        override fun <R, P> accept(v: AnnotationValueVisitor<R, P>, p: P) = v.visitLong(value, p)
    }, Utils.types.getPrimitiveType(TypeKind.LONG))

    override fun valueOf(value: Char): Annotation.Value = JavaxAnnotationImpl.ValueImpl(object : AnnotationValue {
        override fun getValue() = value
        override fun <R, P> accept(v: AnnotationValueVisitor<R, P>, p: P) = v.visitChar(value, p)
    }, Utils.types.getPrimitiveType(TypeKind.CHAR))

    override fun valueOf(value: Float): Annotation.Value = JavaxAnnotationImpl.ValueImpl(object : AnnotationValue {
        override fun getValue() = value
        override fun <R, P> accept(v: AnnotationValueVisitor<R, P>, p: P) = v.visitFloat(value, p)
    }, Utils.types.getPrimitiveType(TypeKind.FLOAT))

    override fun valueOf(value: Double): Annotation.Value = JavaxAnnotationImpl.ValueImpl(object : AnnotationValue {
        override fun getValue() = value
        override fun <R, P> accept(v: AnnotationValueVisitor<R, P>, p: P) = v.visitDouble(value, p)
    }, Utils.types.getPrimitiveType(TypeKind.DOUBLE))

    override fun valueOf(value: String): Annotation.Value = JavaxAnnotationImpl.ValueImpl(object : AnnotationValue {
        override fun getValue() = value
        override fun <R, P> accept(v: AnnotationValueVisitor<R, P>, p: P) = v.visitString(value, p)
    }, Utils.stringType.asType())

    override fun valueOf(value: Type): Annotation.Value {
        // TODO: What if the attribute's type is a wildcard? - Use target
        require(value is JavaxTypeImpl)
        return JavaxAnnotationImpl.ValueImpl(object : AnnotationValue {
            override fun getValue() = value.impl
            override fun <R, P> accept(v: AnnotationValueVisitor<R, P>, p: P) = v.visitType(value.impl, p)
        }, Utils.types.getDeclaredType(Utils.classType, value.impl))
    }

    override fun valueOf(value: Annotation): Annotation.Value {
        require(value is JavaxAnnotationImpl) { "value" }
        return JavaxAnnotationImpl.ValueImpl(object : AnnotationValue {
            override fun getValue() = value.platformModel
            override fun <R, P> accept(v: AnnotationValueVisitor<R, P>, p: P) = v.visitAnnotation(value.impl, p)
        }, value.annotationClass.impl.asType())
    }

    override fun valueOf(enum: Type, constant: String): Annotation.Value {
        val declaration = enum.declaration
        require(declaration.kind == TypeDeclarationKind.Enum) { "enum" }
        require(declaration is JavaxTypeDeclarationImpl) { "enum" }
        val field = declaration.fields.find { it.name == constant } as? JavaxFieldImpl
        requireNotNull(field) { "constant" }
        // TODO: Sus about working with enums
        return JavaxAnnotationImpl.ValueImpl(object : AnnotationValue {
            override fun getValue() = field.platformModel
            override fun <R, P> accept(v: AnnotationValueVisitor<R, P>, p: P) = v.visitEnumConstant(field.platformModel, p)
        }, declaration.type)
    }

    @Suppress("UNCHECKED_CAST")
    override fun valueOf(value: List<Annotation.Value>): Annotation.Value {
        require(value.all { it is JavaxAnnotationImpl.ValueImpl }) { "value" }
        value as List<JavaxAnnotationImpl.ValueImpl>
        val array = value.map { it.value }
        // TODO: nulltype?
        val type = value.firstOrNull()?.typeImpl ?: Utils.types.nullType
        return JavaxAnnotationImpl.ValueImpl(object : AnnotationValue {
            override fun getValue() = array
            override fun <R, P> accept(v: AnnotationValueVisitor<R, P>, p: P) = v.visitArray(array, p)
        }, type)
    }
}
