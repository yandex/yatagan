package com.yandex.daggerlite.lang

/**
 * Model a language construct that can be annotated.
 */
interface AnnotatedLangModel {
    /**
     * All annotations present of the construct.
     */
    val annotations: Sequence<Annotation>

    /**
     * Checks whether the construct is annotated with an [Annotation] of the given [type].
     *
     * @param type Java annotation class to check
     */
    fun <A : kotlin.Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return annotations.any { it.annotationClass.isClass(type) }
    }
}