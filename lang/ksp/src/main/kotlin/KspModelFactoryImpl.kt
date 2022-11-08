package com.yandex.yatagan.lang.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.Variance
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.lang.TypeDeclaration

class KspModelFactoryImpl : LangModelFactory {
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
        parameter as KspTypeImpl
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
        return KspTypeDeclarationImpl(KspTypeImpl(declaration.asType(emptyList())))
    }

    override val errorType: Type
        get() = KspTypeImpl(ErrorTypeImpl)

    override val isInRuntimeEnvironment: Boolean
        get() = false
}
