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
}