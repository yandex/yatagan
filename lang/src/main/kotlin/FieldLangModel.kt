package com.yandex.daggerlite.core.lang

/**
 * Models a field from a JVM point of view.
 * Properties are not modeled by this.
 */
interface FieldLangModel : MemberLangModel {

    /**
     * Type of the field.
     */
    val type: TypeLangModel

    override fun <R> accept(visitor: MemberLangModel.Visitor<R>): R {
        return visitor.visitField(this)
    }
}