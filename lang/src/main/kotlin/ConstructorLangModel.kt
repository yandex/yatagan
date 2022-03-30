package com.yandex.daggerlite.core.lang

interface ConstructorLangModel : CallableLangModel, AnnotatedLangModel, Accessible {
    /**
     * An owner and a constructed type of the constructor.
     */
    val constructee: TypeDeclarationLangModel

    override fun <T> accept(visitor: CallableLangModel.Visitor<T>): T {
        return visitor.visitConstructor(this)
    }
}