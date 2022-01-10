package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.base.setOf
import com.yandex.daggerlite.graph.ConditionScope.LiteralBase

internal fun solveContains(a: ConjunctiveNormalForm, b: ConjunctiveNormalForm): Boolean {
    val cnf = buildNegativeImplicationCNF(source = a, target = b)
    return davisPutnamLogemannLoveland(cnf)
}

/**
 * A set (Conjunction) of sets (disjunctions) of literals.
 */
private typealias ConjunctiveNormalForm = Set<Set<LiteralBase>>
private typealias MutableConjunctiveNormalForm = MutableSet<Set<LiteralBase>>

private fun ConjunctiveNormalForm.splitBy(literal: LiteralBase): ConjunctiveNormalForm {
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
        unitPropagate(HashSet<Set<LiteralBase>>(input.size - 1, 1.0f).apply {
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

private class FakeLiteral private constructor() : LiteralBase {
    private val negative = object : LiteralBase {
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
    val negatedDCNF = hashSetOf<Set<LiteralBase>>()
    val fakeDisjuncts = hashSetOf<LiteralBase>()
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
