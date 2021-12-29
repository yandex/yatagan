package com.yandex.daggerlite.core.lang

interface MemberLangModel : AnnotatedLangModel {
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