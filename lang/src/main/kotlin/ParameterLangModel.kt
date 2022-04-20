package com.yandex.daggerlite.core.lang

/**
 * Models a [FunctionLangModel] parameter.
 */
interface ParameterLangModel : AnnotatedLangModel {
    /**
     * Parameter name.
     */
    val name: String

    /**
     * Parameter type.
     */
    val type: TypeLangModel

    /**
     * [com.yandex.daggerlite.Assisted] annotation model, or `null` if none present.
     */
    val assistedAnnotationIfPresent: AssistedAnnotationLangModel?
}