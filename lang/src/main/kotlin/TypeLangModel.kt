package com.yandex.daggerlite.core.lang

/**
 * Models a concrete [TypeDeclarationLangModel] usage.
 * Contains additional information, like type arguments.
 * **Represents only non-nullable type**. It must be an error to try and represent a nullable type.
 */
interface TypeLangModel {
    /**
     * The corresponding type declaration.
     */
    val declaration: TypeDeclarationLangModel

    /**
     * Type arguments.
     *
     * NOTE: Every returned type is implicitly [decayed][decay].
     */
    val typeArguments: Collection<TypeLangModel>

    /**
     * Checks if the type is `boolean` or `java.lang.Boolean` (`kotlin.Boolean`) type.
     */
    val isBoolean: Boolean

    /**
     * Checks if the type is the `void` (`kotlin.Unit`) type.
     */
    val isVoid: Boolean

    /**
     * Checks if a variable of this type can be assigned a value of [another] type.
     */
    fun isAssignableFrom(another: TypeLangModel): Boolean

    /**
     * This API is inspired by C++'s `std::decay` - the type "decays" into some other type according to some
     * well-defined rules.
     *
     * @return
     *  If this is wildcard type (type with non-invariant variance), returns upper *xor* lower bound.
     *  If this is a java builtin type, returns its *boxed* counterpart.
     *  In any other cases, just returns the original type.
     *  In all the cases, nullability info is discarded.
     */
    fun decay(): TypeLangModel
}