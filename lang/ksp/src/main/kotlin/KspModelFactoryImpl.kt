package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.Variance
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import javax.inject.Provider

class KspModelFactoryImpl : LangModelFactory {
    private val listDeclaration = checkNotNull(Utils.resolver.getClassDeclarationByName(List::class.java.canonicalName)) {
        "Not reached: unable to define list declaration"
    }
    private val mapDeclaration = checkNotNull(Utils.resolver.getClassDeclarationByName(Map::class.java.canonicalName)) {
        "Not reached: unable to define map declaration"
    }
    private val collectionDeclaration = checkNotNull(Utils.resolver.getClassDeclarationByName(Collection::class.java.canonicalName)) {
        "Not reached: unable to define collection declaration"
    }
    private val providerDeclaration = checkNotNull(Utils.resolver.getClassDeclarationByName(Provider::class.java.canonicalName)) {
        "Not reached: unable to define collection declaration"
    }

    override fun getListType(type: TypeLangModel, isCovariant: Boolean): TypeLangModel {
        type as KspTypeImpl
        with(Utils.resolver) {
            val argument = getTypeArgument(
                typeRef = type.impl.asReference(),
                variance = if (isCovariant) Variance.COVARIANT else Variance.INVARIANT,
            )
            return KspTypeImpl(
                reference = listDeclaration.asType(listOf(argument)).asReference(),
                typePosition = TypeMapCache.Position.Parameter,
            )
        }
    }

    override fun getMapType(keyType: TypeLangModel, valueType: TypeLangModel, isCovariant: Boolean): TypeLangModel {
        keyType as KspTypeImpl
        valueType as KspTypeImpl
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

    override fun getCollectionType(type: TypeLangModel): TypeLangModel {
        with(Utils.resolver) {
            val argument = getTypeArgument((type as KspTypeImpl).impl.asReference(), Variance.INVARIANT)
            return KspTypeImpl(collectionDeclaration.asType(listOf(argument)))
        }
    }

    override fun getProviderType(type: TypeLangModel): TypeLangModel {
        with(Utils.resolver) {
            val argument = getTypeArgument((type as KspTypeImpl).impl.asReference(), Variance.INVARIANT)
            return KspTypeImpl(providerDeclaration.asType(listOf(argument)))
        }
    }

    override fun getTypeDeclaration(qualifiedName: String): TypeDeclarationLangModel? {
        val declaration = Utils.resolver.getClassDeclarationByName(qualifiedName) ?: return null
        return KspTypeDeclarationImpl(KspTypeImpl(declaration.asType(emptyList())))
    }

    override val errorType: TypeLangModel
        get() = KspTypeImpl(ErrorTypeImpl)
}
