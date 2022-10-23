package com.yandex.daggerlite.lang

/**
 * An interface that provides an API to create `lang`-level model objects.
 */
interface LangModelFactory {

    enum class ParameterizedType {
        /**
         * `java.util.List`
         */
        List,

        /**
         * `java.util.Set`
         */
        Set,

        /**
         * `java.util.Collection`
         */
        Collection,

        /**
         * `javax.inject.Provider`
         */
        Provider,
    }

    /**
     * Obtains a map type.
     *
     * @param keyType key type for a map
     * @param valueType value type for a map
     * @param isCovariant `true` if the resulting type needs to be covariant over value type
     * `? extends V`/`out V`, where V - [valueType])
     *
     * @return a `java.util.Map` type, parameterized by the given [keyType] and [valueType].
     */
    fun getMapType(keyType: Type, valueType: Type, isCovariant: Boolean = false): Type

    /**
     * Obtains a parameterized type.
     *
     * @return the resulting parameterized type
     *
     * @param type one of available parameterized type declarations
     * @param parameter type parameter to use
     * @param isCovariant `true` if the resulting type needs to be covariant
     * (`? extends T`/`out T`, where T - [parameter])
     */
    fun getParameterizedType(
        type: ParameterizedType,
        parameter: Type,
        isCovariant: Boolean,
    ): Type

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
    val errorType: Type

    /**
     * `true` if the code runs in RT mode (using reflection). `false` if codegen mode.
     */
    val isInRuntimeEnvironment: Boolean

    @OptIn(InternalLangApi::class)
    companion object : LangModelFactory {
        @InternalLangApi
        var delegate: LangModelFactory? = null

        override fun getParameterizedType(
            type: ParameterizedType,
            parameter: Type,
            isCovariant: Boolean,
        ): Type {
            return checkNotNull(delegate).getParameterizedType(type, parameter, isCovariant)
        }

        override fun getMapType(keyType: Type, valueType: Type, isCovariant: Boolean): Type {
            return checkNotNull(delegate).getMapType(keyType, valueType, isCovariant)
        }

        override fun getTypeDeclaration(
            packageName: String,
            simpleName: String,
            vararg simpleNames: String
        ): TypeDeclarationLangModel? = checkNotNull(delegate).getTypeDeclaration(packageName, simpleName, *simpleNames)

        override val errorType: Type get() = checkNotNull(delegate).errorType

        override val isInRuntimeEnvironment: Boolean get() = checkNotNull(delegate).isInRuntimeEnvironment
    }
}
