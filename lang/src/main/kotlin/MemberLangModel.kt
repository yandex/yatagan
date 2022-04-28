package com.yandex.daggerlite.core.lang

/**
 * Models a type declaration member.
 */
interface MemberLangModel : AnnotatedLangModel, HasPlatformModel, Accessible {
    /**
     * Type declaration that this entity is member of.
     */
    val owner: TypeDeclarationLangModel

    /**
     * Whether the member is truly static (@[JvmStatic] or `static`).
     */
    val isStatic: Boolean

    /**
     * Member name.
     */
    val name: String

    interface Visitor<R> {
        fun visitFunction(model: FunctionLangModel): R
        fun visitField(model: FieldLangModel): R
    }

    fun <R> accept(visitor: Visitor<R>): R
}