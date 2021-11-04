package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.core.lang.TypeDeclarationLangModel

interface ConditionScope {
    val expression: Set<Set<Literal>>

    fun contains(another: ConditionScope): Boolean

    infix fun and(rhs: ConditionScope): ConditionScope

    infix fun or(rhs: ConditionScope): ConditionScope

    operator fun not(): ConditionScope

    interface Literal {
        val negated: Boolean
        val root: TypeDeclarationLangModel
        val path: List<MemberLangModel>

        operator fun not(): Literal
    }

    companion object
}

val ConditionScope.isEmpty get() = expression.isEmpty()
val ConditionScope.isNotEmpty get() = !isEmpty