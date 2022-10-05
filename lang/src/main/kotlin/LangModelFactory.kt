package com.yandex.daggerlite.core.lang

/**
 * An interface that provides an API to create `lang`-level model objects.
 */
interface LangModelFactory {
    /**
     * Creates a list type.
     *
     * @return a `java.util.List` type, parameterized by the given [type].
     *
     * @param isCovariant whether to yield a `? extends [T][type]` wildcard as the list type parameter.
     */
    fun getListType(type: TypeLangModel, isCovariant: Boolean = false): TypeLangModel

    /**
     * Creates a map type.
     *
     * @return a `java.util.Map` type, parameterized by the given [keyType] and [valueType].
     */
    fun getMapType(keyType: TypeLangModel, valueType: TypeLangModel, isCovariant: Boolean = false): TypeLangModel

    /**
     * Creates a collection type.
     *
     * @return a `java.util.Collection` type, parameterized by the given [type].
     */
    fun getCollectionType(type: TypeLangModel): TypeLangModel

    /**
     * Creates a parameterized `javax.inject.Provider` type.
     *
     * @return a `javax.inject.Provider` type, parameterized by the given [type].
     */
    fun getProviderType(type: TypeLangModel): TypeLangModel

    /**
     * Gets a type declaration by the fully qualified name ('.'-separated).
     *
     * @return a type declaration model by the given name. If kotlin-platform type is requested, e. g. `kotlin.String`,
     * Java counterpart is returned, e.g. `java.lang.String`. `null` is returned when no such type can be found.
     *
     * @param packageName package name where the class is located.
     * @param simpleName a single simple class name.
     * @param simpleNames multiple names if the class is nested, empty if the class is top-level.
     */
    fun getTypeDeclaration(
        packageName: String,
        simpleName: String,
        vararg simpleNames: String,
    ): TypeDeclarationLangModel?

    /**
     * An "error" type, which is usually applicable in places, where type information is unresolved due to a semantic
     * error in the underlying code.
     */
    val errorType: TypeLangModel

    /**
     * `true` if the code runs in RT mode (using reflection). `false` if codegen mode.
     */
    val isInRuntimeEnvironment: Boolean

    @OptIn(InternalLangApi::class)
    companion object : LangModelFactory {
        @InternalLangApi
        var delegate: LangModelFactory? = null

        override fun getListType(type: TypeLangModel, isCovariant: Boolean): TypeLangModel {
            return checkNotNull(delegate).getListType(type, isCovariant)
        }

        override fun getMapType(keyType: TypeLangModel, valueType: TypeLangModel, isCovariant: Boolean): TypeLangModel {
            return checkNotNull(delegate).getMapType(keyType, valueType, isCovariant)
        }

        override fun getCollectionType(type: TypeLangModel) =
                checkNotNull(delegate).getCollectionType(type)

        override fun getProviderType(type: TypeLangModel): TypeLangModel {
            return checkNotNull(delegate).getProviderType(type)
        }

        override fun getTypeDeclaration(
            packageName: String,
            simpleName: String,
            vararg simpleNames: String
        ): TypeDeclarationLangModel? =
                checkNotNull(delegate).getTypeDeclaration(packageName, simpleName, *simpleNames)

        override val errorType: TypeLangModel get() = checkNotNull(delegate).errorType

        override val isInRuntimeEnvironment: Boolean get() = checkNotNull(delegate).isInRuntimeEnvironment
    }
}
