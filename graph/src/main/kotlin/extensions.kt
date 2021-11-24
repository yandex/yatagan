package com.yandex.daggerlite.core

fun ConditionScope.Literal.normalized(): ConditionScope.Literal {
    return if (negated) !this else this
}