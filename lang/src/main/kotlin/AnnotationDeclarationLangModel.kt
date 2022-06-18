package com.yandex.daggerlite.core.lang

/**
 * An annotation class declaration.
 */
interface AnnotationDeclarationLangModel : AnnotatedLangModel {
    interface Attribute {
        val name: String
        val type: TypeLangModel
    }

    /**
     * Checks whether the annotation class is given JVM type.
     *
     * @param clazz Java class to check
     */
    fun isClass(clazz: Class<out Annotation>): Boolean

    val attributes: Sequence<Attribute>
}