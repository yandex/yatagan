package com.yandex.daggerlite.lang.jap

import com.yandex.daggerlite.lang.LangModelFactory
import com.yandex.daggerlite.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.lang.TypeLangModel
import javax.lang.model.element.TypeElement

class JavaxModelFactoryImpl : LangModelFactory {
    private val listElement: TypeElement by lazy {
        Utils.elements.getTypeElement(java.util.List::class.java.canonicalName)
    }
    private val setElement: TypeElement by lazy {
        Utils.elements.getTypeElement(java.util.Set::class.java.canonicalName)
    }
    private val collectionElement: TypeElement by lazy {
        Utils.elements.getTypeElement(java.util.Collection::class.java.canonicalName)
    }
    private val mapElement: TypeElement by lazy {
        Utils.elements.getTypeElement(java.util.Map::class.java.canonicalName)
    }
    private val providerElement: TypeElement by lazy {
        Utils.elements.getTypeElement(javax.inject.Provider::class.java.canonicalName)
    }

    override fun getMapType(keyType: TypeLangModel, valueType: TypeLangModel, isCovariant: Boolean): TypeLangModel {
        keyType as JavaxTypeImpl
        valueType as JavaxTypeImpl
        with(Utils.types) {
            val valueArgType =
                if (isCovariant) getWildcardType(/*extends*/ valueType.impl, /*super*/ null)
                else valueType.impl
            return JavaxTypeImpl(getDeclaredType(mapElement, keyType.impl, valueArgType))
        }
    }

    override fun getParameterizedType(
        type: LangModelFactory.ParameterizedType,
        parameter: TypeLangModel,
        isCovariant: Boolean,
    ): TypeLangModel {
        parameter as JavaxTypeImpl
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
    ): TypeDeclarationLangModel? {
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

    override val errorType: TypeLangModel
        get() = JavaxTypeImpl(Utils.types.nullType)

    override val isInRuntimeEnvironment: Boolean
        get() = false
}
