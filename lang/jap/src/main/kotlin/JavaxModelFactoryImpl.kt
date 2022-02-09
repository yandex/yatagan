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

    override fun getTypeDeclaration(qualifiedName: String): TypeDeclarationLangModel {
        return JavaxTypeDeclarationImpl(Utils.elements.getTypeElement(qualifiedName).asType().asDeclaredType())
    }

    override val errorType: TypeLangModel
        get() = JavaxTypeImpl(ErrorTypeImpl)
}
