package com.yandex.daggerlite.core.lang

/**
 * Models annotation instance of any class.
 * Suits for modeling annotation of user-defined types or framework annotations, that have no attributes.
 *
 * This interface doesn't expose annotation attributes; to be able to get that, use class-specific annotation model,
 * e. g. [ComponentAnnotationLangModel], ... .
 */
interface AnnotationLangModel {
    /**
     * Annotation class declaration.
     */
    val annotationClass: AnnotationDeclarationLangModel
}

