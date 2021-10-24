package com.yandex.daggerlite.core.lang

/**
 * Models Annotation with all its arguments
 */
interface AnnotationLangModel {
    /**
     * Whether annotation type is annotated with `javax.inject.Scope`.
     */
    val isScope: Boolean

    /**
     * Whether annotation type is annotated with `javax.inject.Qualifier`.
     */
    val isQualifier: Boolean

    /**
     * Checks whether the annotation has given JVM type.
     */
    fun <A : Annotation> hasType(type: Class<A>): Boolean

    /**
     * Returns the [attribute] value as boolean. Must handle default values.
     * If type of the actual attribute is not [Boolean] then the behavior is undefined.
     */
    fun getBoolean(attribute: String): Boolean

    /**
     * Returns the [attribute] value as an array of types. Must handle default values.
     * If type of the actual attribute is not the array of types then the behavior is undefined.
     */
    fun getTypes(attribute: String): Sequence<TypeLangModel>

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}
