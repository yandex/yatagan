package com.yandex.yatagan.lang

/**
 * Model a language construct that can be annotated.
 */
public interface Annotated {
    /**
     * All annotations present of the construct.
     */
    public val annotations: Sequence<Annotation>

    /**
     * Checks whether the construct is annotated with an [Annotation] of the given [type].
     *
     * @param type Java annotation class to check
     */
    public fun <A : kotlin.Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return annotations.any { it.annotationClass.isClass(type) }
    }
}