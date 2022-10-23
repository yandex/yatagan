package com.yandex.daggerlite.lang

/**
 * Represents a function/method associated with a class from **the Java point of view**.
 * - Constructor is modeled separately by [ConstructorLangModel].
 * - Top-level kotlin functions are not covered.
 * - Kotlin properties (setters and getters) are also represented by this.
 */
interface FunctionLangModel : Member, CallableLangModel, Comparable<FunctionLangModel> {
    /**
     * Whether the function is abstract.
     */
    val isAbstract: Boolean

    /**
     * Return type of the function.
     */
    val returnType: Type

    /**
     * [com.yandex.daggerlite.Provides] annotation model if present. `null` if absent.
     */
    val providesAnnotationIfPresent: ProvidesAnnotationLangModel?

    /**
     * [com.yandex.daggerlite.IntoList] annotation model if present. `null` if absent.
     */
    val intoListAnnotationIfPresent: IntoCollectionAnnotationLangModel?

    /**
     * [com.yandex.daggerlite.IntoSet] annotation model if present. `null` if absent.
     */
    val intoSetAnnotationIfPresent: IntoCollectionAnnotationLangModel?
}