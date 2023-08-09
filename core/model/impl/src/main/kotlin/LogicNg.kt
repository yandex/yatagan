/*
 * Copyright 2023 Yandex LLC
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

package com.yandex.yatagan.core.model.impl

import org.logicng.datastructures.Tristate
import org.logicng.formulas.FormulaFactory
import org.logicng.formulas.FormulaPredicate
import org.logicng.formulas.cache.PredicateCacheEntry
import org.logicng.predicates.satisfiability.SATPredicate
import org.logicng.solvers.sat.MiniSatConfig

internal object LogicNg {
    val formulaFactory = FormulaFactory().apply {
        putConfiguration(MiniSatConfig.builder()
            .clMinimization(MiniSatConfig.ClauseMinimization.DEEP)
            .cnfMethod(MiniSatConfig.CNFMethod.FULL_PG_ON_SOLVER)
            .incremental(false)
            .build())
    }

    private val satPredicate = SATPredicate(formulaFactory)

    val tautologyPredicate = FormulaPredicate { formula, cache ->
        when (formula.predicateCacheEntry(PredicateCacheEntry.IS_TAUTOLOGY)) {
            Tristate.FALSE -> false
            Tristate.TRUE -> true
            Tristate.UNDEF -> (!formula.negate().holds(satPredicate))
                .also { if (cache) formula.setPredicateCacheEntry(PredicateCacheEntry.IS_TAUTOLOGY, it) }

            null -> throw AssertionError()
        }
    }

    val contradictionPredicate = FormulaPredicate { formula, cache ->
        !satPredicate.test(formula, cache)
    }
}
