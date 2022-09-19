package com.yandex.daggerlite.core.lang

/**
 * An annotation class declaration.
 */
interface AnnotationDeclarationLangModel : AnnotatedLangModel {
    /**
     * Represents an annotation class' property/@interface's method.
     */
    interface Attribute {
        /**
         * Name of the attribute.
         */
        val name: String

        /**
         * Type of the attribute.
         */
        val type: TypeLangModel
    }

    /**
     * Checks whether the annotation class is given JVM type.
     *
     * @param clazz Java class to check
     */
    fun isClass(clazz: Class<out Annotation>): Boolean

    /**
     * Attributes (annotation class' properties for Kotlin and @interface's methods for Java).
     */
    val attributes: Sequence<Attribute>

    /**
     * Computes annotation retention.
     *
     * @return annotation retention in Kotlin terms.
     *
     * @see java.lang.annotation.Retention
     * @see kotlin.annotation.Retention
     */
    fun getRetention(): AnnotationRetention
}