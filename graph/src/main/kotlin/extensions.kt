package com.yandex.daggerlite.graph

fun ConditionScope.Literal.normalized(): ConditionScope.Literal {
    return if (negated) !this else this
}

operator fun GraphEntryPoint.component1() = getter

operator fun GraphEntryPoint.component2() = dependency