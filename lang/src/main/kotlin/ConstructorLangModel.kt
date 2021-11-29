package com.yandex.daggerlite.core.lang

interface ConstructorLangModel : CallableLangModel, AnnotatedLangModel {
    /**
     * An owner and a constructed type of the constructor.
     */
    val constructee: TypeDeclarationLangModel
}