package com.yandex.daggerlite.graph

import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

interface ConditionScope : MayBeInvalid {
    val expression: Set<Set<Literal>>

    operator fun contains(another: ConditionScope): Boolean

    infix fun and(rhs: ConditionScope): ConditionScope

    infix fun or(rhs: ConditionScope): ConditionScope

    operator fun not(): ConditionScope

    val isAlways: Boolean

    val isNever: Boolean

    interface LiteralBase {
        val negated: Boolean
        operator fun not(): LiteralBase
    }

    interface Literal : LiteralBase, MayBeInvalid {
        val root: TypeDeclarationLangModel
        val path: List<MemberLangModel>
        override operator fun not(): Literal
    }

    companion object
}
