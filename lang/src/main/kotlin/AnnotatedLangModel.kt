package com.yandex.daggerlite.core.lang

/**
 * Model a language construct that can be annotated.
 */
interface AnnotatedLangModel {
    /**
     * All annotations present of the construct.
     */
    val annotations: Sequence<AnnotationLangModel>

    /**
     * Checks whether the construct is annotated with an [AnnotationLangModel] of the given [type].
     *
     * @param type Java annotation class to check
     */
    fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return annotations.any { it.hasType(type) }
    }
}