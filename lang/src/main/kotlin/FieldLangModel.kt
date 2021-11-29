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
}