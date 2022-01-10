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

    /**
     * [com.yandex.daggerlite.IntoList] annotation model if present. `null` if absent.
     */
    val intoListAnnotationIfPresent: IntoListAnnotationLangModel?

    /**
     * [com.yandex.daggerlite.DeclareList] annotation model if present. `null` if absent.
     */
    val declareListAnnotationIfPresent: DeclareListAnnotationLangModel?

    /**
     * The [kotlin property accessor info][PropertyAccessorInfo] for which this function.
     * `null` if no kotlin property corresponds to this function.
     */
    val propertyAccessorInfo: PropertyAccessorInfo?

    override fun <R> accept(visitor: MemberLangModel.Visitor<R>): R {
        return visitor.visitFunction(this)
    }

    interface PropertyAccessorInfo {
        /**
         * Name of the property that the accessor corresponds to.
         */
        val propertyName: String

        /**
         * Accessor kind.
         */
        val kind: PropertyAccessorKind
    }

    enum class PropertyAccessorKind {
        Getter,
        Setter,
    }
}