package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import javax.lang.model.element.TypeElement

class JavaxModelFactoryImpl : LangModelFactory {
    private val listElement: TypeElement by lazy {
        Utils.elements.getTypeElement(java.util.List::class.java.canonicalName)
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

    override fun getListType(type: TypeLangModel, isCovariant: Boolean): TypeLangModel {
        with(Utils.types) {
            val typeImpl = (type as JavaxTypeImpl).impl
            val argType = if (isCovariant) getWildcardType(/*extends*/ typeImpl,/*super*/ null) else typeImpl
            return JavaxTypeImpl(getDeclaredType(listElement, argType))
        }
    }

    override fun getCollectionType(type: TypeLangModel): TypeLangModel {
        return JavaxTypeImpl(Utils.types.getDeclaredType(collectionElement, (type as JavaxTypeImpl).impl))
    }

    override fun getProviderType(type: TypeLangModel): TypeLangModel {
        return JavaxTypeImpl(Utils.types.getDeclaredType(providerElement, (type as JavaxTypeImpl).impl))
    }

    override fun getTypeDeclaration(qualifiedName: String): TypeDeclarationLangModel? {
        val element = Utils.elements.getTypeElement(qualifiedName) ?: return null
        return JavaxTypeDeclarationImpl(element.asType().asDeclaredType())
    }

    override val errorType: TypeLangModel
        get() = JavaxTypeImpl(Utils.types.nullType)
}
