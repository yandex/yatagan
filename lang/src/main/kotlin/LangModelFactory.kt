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
     * Creates a collection type.
     *
     * @return a `java.util.Collection` type, parameterized by the given [type].
     */
    fun getCollectionType(type: TypeLangModel): TypeLangModel

    /**
     * Gets a type declaration by the fully qualified name ('.'-separated).
     *
     * @return a type declaration model by the given name. If kotlin-platform type is requested, e. g. `kotlin.String`,
     * Java counterpart is returned, e. g. `java.lang.String`. `null` is returned when no such type can be found.
     *
     * @param qualifiedName fully qualified name of the class.
     */
    fun getTypeDeclaration(qualifiedName: String): TypeDeclarationLangModel?

    /**
     * An "error" type, which is usually applicable in places, where type information is unresolved due to a semantic
     * error in the underlying code.
     */
    val errorType: TypeLangModel

    @OptIn(InternalLangApi::class)
    companion object : LangModelFactory {
        @InternalLangApi
        var delegate: LangModelFactory? = null

        override fun getListType(type: TypeLangModel, isCovariant: Boolean): TypeLangModel {
            return checkNotNull(delegate).getListType(type, isCovariant)
        }

        override fun getCollectionType(type: TypeLangModel) =
                checkNotNull(delegate).getCollectionType(type)

        override fun getTypeDeclaration(qualifiedName: String) =
                checkNotNull(delegate).getTypeDeclaration(qualifiedName)

        override val errorType: TypeLangModel get() = checkNotNull(delegate).errorType
    }
}
