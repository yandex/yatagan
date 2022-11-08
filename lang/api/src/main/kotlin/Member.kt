package com.yandex.yatagan.lang

/**
 * Models a type declaration member.
 */
interface Member : Annotated, HasPlatformModel, Accessible {
    /**
     * Type declaration that this entity is member of.
     */
    val owner: TypeDeclaration

    /**
     * Whether the member is truly static (@[JvmStatic] or `static`).
     */
    val isStatic: Boolean

    /**
     * Member name.
     */
    val name: String

    interface Visitor<R> {
        fun visitMethod(model: Method): R
        fun visitField(model: Field): R
    }

    fun <R> accept(visitor: Visitor<R>): R
}