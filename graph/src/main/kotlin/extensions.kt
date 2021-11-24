package com.yandex.daggerlite.graph

fun ConditionScope.Literal.normalized(): ConditionScope.Literal {
    return if (negated) !this else this
}