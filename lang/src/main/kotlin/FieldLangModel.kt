package com.yandex.daggerlite.core.lang

interface FieldLangModel : MemberLangModel {

    /**
     * Type of the field.
     */
    val type: TypeLangModel

    override fun <R> accept(visitor: MemberLangModel.Visitor<R>): R {
        return visitor.visitField(this)
    }
}