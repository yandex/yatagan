package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import javax.inject.Provider

class RtModelFactoryImpl : LangModelFactory {
    override fun getListType(type: TypeLangModel, isCovariant: Boolean): TypeLangModel {
        type as RtTypeImpl
        val arg = if (isCovariant) WildcardTypeImpl(upperBound = type.impl) else type.impl
        return RtTypeImpl(ParameterizedTypeImpl(arg, raw = List::class.java))
    }

    override fun getMapType(keyType: TypeLangModel, valueType: TypeLangModel, isCovariant: Boolean): TypeLangModel {
        valueType as RtTypeImpl
        val valueArg = if (isCovariant) WildcardTypeImpl(upperBound = valueType.impl) else valueType.impl
        return RtTypeImpl(ParameterizedTypeImpl((keyType as RtTypeImpl).impl, valueArg, raw = Map::class.java))
    }

    override fun getCollectionType(type: TypeLangModel): TypeLangModel {
        return RtTypeImpl(ParameterizedTypeImpl((type as RtTypeImpl).impl, raw = Collection::class.java))
    }

    override fun getProviderType(type: TypeLangModel): TypeLangModel {
        return RtTypeImpl(ParameterizedTypeImpl((type as RtTypeImpl).impl, raw = Provider::class.java))
    }

    override fun getTypeDeclaration(qualifiedName: String): TypeDeclarationLangModel? {
        return try {
            RtTypeDeclarationImpl(RtTypeImpl(Class.forName(qualifiedName)))
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    override val errorType: TypeLangModel
        get() = RtTypeImpl(ErrorType())

    private class ErrorType : Type {
        override fun toString() = "<error>"
    }

    private class WildcardTypeImpl(
        private val upperBound: Type,
    ) : WildcardType {
        private val upperBounds = arrayOf(upperBound)
        override fun getUpperBounds() = upperBounds
        override fun getLowerBounds() = emptyArray<Type>()
        override fun toString() = "? extends $upperBound"
    }

    private class ParameterizedTypeImpl(
        private vararg val arguments: Type,
        private val raw: Type,
    ) : ParameterizedType {
        override fun getActualTypeArguments() = arguments
        override fun getRawType() = raw
        override fun getOwnerType() = null
        override fun toString() = buildString {
            append(raw)
            arguments.joinTo(this, prefix = "<", postfix = ">")
        }
    }
}
