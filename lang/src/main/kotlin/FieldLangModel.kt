package com.yandex.daggerlite.core.lang

interface FieldLangModel : MemberLangModel {
    /**
     * Type that this field is associated with.
     */
    val owner: TypeDeclarationLangModel

    /**
     * Type of the field.
     */
    val type: TypeLangModel

    override fun <R> accept(visitor: MemberLangModel.Visitor<R>): R {
        return visitor.visitField(this)
    }
}