package com.yandex.daggerlite.lang

/**
 * Models a concrete [TypeDeclarationLangModel] usage.
 * Contains additional information, like type arguments.
 *
 * No assumptions about type nullability and other "enhancements" is made.
 *
 * The represented type can be any normal Java type: reference, primitive, array, etc., except the wildcard types.
 *
 * Regarding wildcard types.
 * This model can't represent a wildcard type directly - the system has Kotlin point of view here. Kotlin models
 * type parameter as a *type variable* and a *variance*, and type argument as a *type* and a *projection*.
 * [TypeLangModel] only provides [typeArguments]; variance, projection and type variables are not exposed as of now.
 *
 */
interface TypeLangModel : Comparable<TypeLangModel> {
    /**
     * The corresponding type declaration.
     */
    val declaration: TypeDeclarationLangModel

    /**
     * Type arguments. If any of the arguments has non-invariant variance (or a wildcard type) -
     * such is info is not available via the current API.
     */
    val typeArguments: List<TypeLangModel>

    /**
     * Checks if the type is the `void` JVM type.
     */
    val isVoid: Boolean

    /**
     * Checks if a variable of this type can be assigned a value of [another] type.
     */
    fun isAssignableFrom(another: TypeLangModel): Boolean

    /**
     * @return If this is a primitive type, returns its *boxed* counterpart. Returns this type otherwise.
     *
     */
    fun asBoxed(): TypeLangModel
}