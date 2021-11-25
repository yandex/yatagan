package com.yandex.daggerlite.core.lang

/**
 * Represents a function/method associated with a class from **the Java point of view**.
 * - Constructor is modeled separately by [ConstructorLangModel].
 * - Top-level kotlin functions are not covered.
 * - Kotlin properties (setters and getters) are represented by this.
 */
interface FunctionLangModel : MemberLangModel, CallableLangModel {
    /**
     * Type that this function is associated with.
     */
    val owner: TypeDeclarationLangModel

    /**
     * Whether the function is abstract.
     */
    val isAbstract: Boolean

    /**
     * Return type of the function.
     */
    val returnType: TypeLangModel

    /**
     * [com.yandex.daggerlite.Provides] annotation model if present. `null` if absent.
     */
    val providesAnnotationIfPresent: ProvidesAnnotationLangModel?
}