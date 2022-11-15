package com.yandex.yatagan.lang

/**
 * Models a type declaration member.
 */
public interface Member : Annotated, HasPlatformModel, Accessible {
    /**
     * Type declaration that this entity is member of.
     */
    public val owner: TypeDeclaration

    /**
     * Whether the member is truly static (@[JvmStatic] or `static`).
     */
    public val isStatic: Boolean

    /**
     * Member name.
     */
    public val name: String

    public interface Visitor<R> {
        public fun visitMethod(model: Method): R
        public fun visitField(model: Field): R
    }

    public fun <R> accept(visitor: Visitor<R>): R
}