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
     */
    fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return annotations.any { it.hasType(type) }
    }

    /**
     * Returns an [AnnotationLangModel] of the given [type]. If such annotation is not present, the behavior is
     * undefined.
     */
    fun <A : Annotation> getAnnotation(type: Class<A>): AnnotationLangModel {
        return annotations.first { it.hasType(type) }
    }
}