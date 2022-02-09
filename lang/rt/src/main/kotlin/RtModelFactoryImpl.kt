package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

class RtModelFactoryImpl : LangModelFactory {
    override fun getListType(type: TypeLangModel, isCovariant: Boolean): TypeLangModel {
        type as RtTypeImpl
        val arg = if (isCovariant) {
            object : WildcardType {
                override fun getUpperBounds(): Array<Type> = arrayOf(type.impl)
                override fun getLowerBounds(): Array<Type> = emptyArray()
                override fun toString() = "? extends $type"
            }
        } else type.impl
        return RtTypeImpl(object : ParameterizedType {
            override fun getActualTypeArguments(): Array<Type> = arrayOf(arg)
            override fun getRawType(): Type = List::class.java
            override fun getOwnerType(): Type? = null
            override fun toString() = "java.util.List<$type>"
        })
    }

    override fun getCollectionType(type: TypeLangModel): TypeLangModel {
        type as RtTypeImpl
        return RtTypeImpl(object : ParameterizedType {
            override fun getActualTypeArguments(): Array<Type> = arrayOf(type.impl)
            override fun getRawType(): Type = Collection::class.java
            override fun getOwnerType(): Type? = null
            override fun toString() = "java.util.Collection<${type.impl}>"
        })
    }

    override fun getTypeDeclaration(qualifiedName: String): TypeDeclarationLangModel {
        return RtTypeDeclarationImpl(RtTypeImpl(Class.forName(qualifiedName)))
    }

    override val errorType: TypeLangModel
        get() = RtTypeImpl(ErrorType())

    private class ErrorType : Type {
        override fun toString() = "<error>"
    }
}
