package com.yandex.daggerlite.graph

/**
 * Discards negation from the literal.
 *
 * @return `!this` if negated, `this` otherwise.
 */
fun ConditionScope.Literal.normalized(): ConditionScope.Literal {
    return if (negated) !this else this
}

operator fun GraphEntryPoint.component1() = getter

operator fun GraphEntryPoint.component2() = dependency
