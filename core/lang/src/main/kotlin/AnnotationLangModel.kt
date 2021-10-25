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

    override fun equals(other: Any?): Boolean
    override fun hashCode(): Int
}

