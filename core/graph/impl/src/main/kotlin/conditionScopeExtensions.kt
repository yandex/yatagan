/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.model.ConditionModel
import com.yandex.yatagan.core.model.ConditionScope

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
