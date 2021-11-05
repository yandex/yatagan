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
     */
    val typeArguments: Collection<TypeLangModel>

    /**
     * TODO: doc.
     */
    val isBoolean: Boolean
}