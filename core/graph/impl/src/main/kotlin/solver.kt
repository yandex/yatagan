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

import com.yandex.yatagan.base.setOf
import com.yandex.yatagan.core.model.ConditionExpression.Literal

internal fun solveContains(a: ConjunctiveNormalForm, b: ConjunctiveNormalForm): Boolean {
    val cnf = buildNegativeImplicationCNF(source = a, target = b)
    return davisPutnamLogemannLoveland(cnf)
}

/**
 * A set (Conjunction) of sets (disjunctions) of literals.
 */
private typealias ConjunctiveNormalForm = Set<Set<Literal>>
private typealias MutableConjunctiveNormalForm = MutableSet<Set<Literal>>

private fun ConjunctiveNormalForm.splitBy(literal: Literal): ConjunctiveNormalForm {
    val negLiteral = !literal
    return buildSet {
        for (clause in this@splitBy) {
            if (literal !in clause) {
                add(clause - negLiteral)
            }
        }
    }
}

private fun unitPropagate(input: ConjunctiveNormalForm): ConjunctiveNormalForm {
    return input.find { clause -> clause.size == 1 }?.let { unitClause ->
        val unitLiteral = unitClause.first()
        val negatedUnitLiteral = !unitLiteral
        unitPropagate(HashSet<Set<Literal>>(input.size - 1, 1.0f).apply {
            for (clause in input) {
                add(when {
                    unitLiteral in clause -> continue
                    negatedUnitLiteral in clause -> clause - negatedUnitLiteral
                    else -> clause
                })
            }
        })
    } ?: input
}

private fun davisPutnamLogemannLoveland(input: ConjunctiveNormalForm): Boolean {
    if (input.isEmpty()) return false
    if (emptySet() in input) return true

    val unitPropagated = unitPropagate(input)
    // MAYBE: add pure literal elimination?

    if (unitPropagated.isEmpty()) return false
    if (emptySet() in unitPropagated) return true

    // MAYBE: consider making literal choice more efficient.
    val literal = unitPropagated.asSequence().flatten().first()
    return davisPutnamLogemannLoveland(unitPropagated.splitBy(literal)) &&
            davisPutnamLogemannLoveland(unitPropagated.splitBy(!literal))
}

private fun buildNegativeImplicationCNF(
    source: ConjunctiveNormalForm,
    target: ConjunctiveNormalForm,
): ConjunctiveNormalForm {
    val negatedDCNF = negateAndBuildDefinitionalCNF(target)
    negatedDCNF += source  // now negative implication CNF
    return negatedDCNF
}

private class FakeLiteral private constructor() : Literal {
    private val negative = object : Literal {
        override val negated get() = true
        override fun not() = this@FakeLiteral
    }

    override val negated get() = false
    override fun not() = negative

    companion object {
        // More than 32 literals are not going to be in use in one expression, are they?
        val Pool = Array(32) { FakeLiteral() }
    }
}

private fun negateAndBuildDefinitionalCNF(cnf: ConjunctiveNormalForm): MutableConjunctiveNormalForm {
    val negatedDCNF = hashSetOf<Set<Literal>>()
    val fakeDisjuncts = hashSetOf<Literal>()
    cnf.forEachIndexed { index, disjuncts ->
        val fakeCondition = FakeLiteral.Pool[index]
        disjuncts.forEach { conjunct ->
            negatedDCNF += setOf(!conjunct, !fakeCondition)
        }
        fakeDisjuncts += fakeCondition
    }
    negatedDCNF += fakeDisjuncts
    return negatedDCNF
}
