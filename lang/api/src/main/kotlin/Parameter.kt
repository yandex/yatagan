package com.yandex.daggerlite.lang

/**
 * Models a [CallableLangModel] parameter.
 */
interface Parameter : AnnotatedLangModel {
    /**
     * Parameter name.
     *
     * _WARNING_: this property should not be relied on, as parameter names' availability may vary.
     *  It's generally safe to use this for error reporting or for method overriding; yet code correctness and public
     *  generated API must not depend on parameter names.
     */
    val name: String

    /**
     * Parameter type.
     */
    val type: Type

    /**
     * [com.yandex.daggerlite.Assisted] annotation model, or `null` if none present.
     */
    val assistedAnnotationIfPresent: AssistedAnnotationLangModel?
}