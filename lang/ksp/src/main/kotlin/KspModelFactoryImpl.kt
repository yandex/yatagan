package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.Variance
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel

class KspModelFactoryImpl : LangModelFactory {
    private val listDeclaration by lazy(LazyThreadSafetyMode.PUBLICATION) {
        checkNotNull(Utils.resolver.getClassDeclarationByName("java.util.List")) {
            "FATAL: Unable to define `java.util.List`, check classpath"
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
            "FATAL: unable to define `javax.inject.Provider` declaration, ensure DL API is present on the classpath"
        }
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

    override fun getTypeDeclaration(
        packageName: String,
        simpleName: String,
        vararg simpleNames: String
    ): TypeDeclarationLangModel? {
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
        return KspTypeDeclarationImpl(KspTypeImpl(declaration.asType(emptyList())))
    }

    override val errorType: TypeLangModel
        get() = KspTypeImpl(ErrorTypeImpl)

    override val isInRuntimeEnvironment: Boolean
        get() = false
}
