package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.ConditionModel
import com.yandex.daggerlite.core.ConditionScope

internal operator fun ConditionScope.contains(another: ConditionScope): Boolean {
    if (another == ConditionScope.Unscoped) return true
    if (this == another) return true
    return solveContains(expression, another.expression)
}

internal infix fun ConditionScope.and(rhs: ConditionScope): ConditionScope {
    if (this == ConditionScope.Unscoped) return rhs
    if (rhs == ConditionScope.Unscoped) return this
    return ConditionScope(expression + rhs.expression)
}

internal infix fun ConditionScope.or(rhs: ConditionScope): ConditionScope {
    if (this == ConditionScope.NeverScoped) return rhs
    if (rhs == ConditionScope.NeverScoped) return this
    return ConditionScope(buildSet {
        for (p in expression) for (q in rhs.expression) {
            add(p + q)
        }
    })
}

internal operator fun ConditionScope.not(): ConditionScope {
    fun impl(
        clause: Set<ConditionModel>,
        rest: Set<Set<ConditionModel>>,
    ): Set<Set<ConditionModel>> {
        return clause.fold(emptySet()) { acc, literal ->
            acc + if (rest.isEmpty()) {
                setOf(setOf(literal))
            } else {
                impl(rest.first(), rest.drop(1).toSet()).map { it + !literal }
            }
        }
    }

    return when (this) {
        ConditionScope.Unscoped -> ConditionScope.NeverScoped
        ConditionScope.NeverScoped -> ConditionScope.Unscoped
        else -> ConditionScope(impl(expression.first(), expression.drop(1).toSet()))
    }
}
