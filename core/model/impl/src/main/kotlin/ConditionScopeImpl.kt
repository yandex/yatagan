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

import com.yandex.yatagan.core.model.BooleanExpression
import com.yandex.yatagan.core.model.ConditionModel
import com.yandex.yatagan.core.model.ConditionScope

internal class ConditionScopeImpl(
    override val expression: BooleanExpressionInternal,
) : ConditionScope.ExpressionScope {
    override fun isTautology(): Boolean {
        return expression.formula.holds(LogicNg.tautologyPredicate)
    }

    override fun isContradiction(): Boolean {
        return expression.formula.holds(LogicNg.contradictionPredicate)
    }

    override fun allConditionModels(): List<ConditionModel> {
        val list = arrayListOf<ConditionModel>()
        expression.accept(object : BooleanExpression.Visitor<Unit> {
            override fun visitVariable(variable: BooleanExpression.Variable) {
                list.add(variable.model)
            }

            override fun visitAnd(and: BooleanExpression.And) {
                and.lhs.accept(this)
                and.rhs.accept(this)
            }

            override fun visitOr(or: BooleanExpression.Or) {
                or.lhs.accept(this)
                or.rhs.accept(this)
            }

            override fun visitNot(not: BooleanExpression.Not) = not.underlying.accept(this)
        })
        return list
    }

    override fun implies(another: ConditionScope): Boolean {
        return when (another) {
            ConditionScope.Always -> true
            ConditionScope.Never -> false
            is ConditionScope.ExpressionScope -> {
                val anotherExpression = another.expression as BooleanExpressionInternal
                expression == anotherExpression ||
                        LogicNg.formulaFactory.implication(expression.formula, anotherExpression.formula)
                            .holds(LogicNg.tautologyPredicate)
            }
        }
    }

    override fun and(another: ConditionScope): ConditionScope {
        return when (another) {
            ConditionScope.Always -> this
            ConditionScope.Never -> ConditionScope.Never
            is ConditionScope.ExpressionScope -> ConditionScopeImpl(AndExpressionImpl(
                lhs = expression,
                rhs = another.expression as BooleanExpressionInternal,
            ))
        }
    }

    override fun or(another: ConditionScope): ConditionScope {
        return when (another) {
            ConditionScope.Always -> ConditionScope.Always
            ConditionScope.Never -> this
            is ConditionScope.ExpressionScope -> ConditionScopeImpl(OrExpressionImpl(
                lhs = expression,
                rhs = another.expression as BooleanExpressionInternal,
            ))
        }
    }

    override fun equals(other: Any?): Boolean {
        return this === other || other is ConditionScopeImpl && expression == other.expression
    }

    override fun hashCode(): Int {
        return expression.hashCode()
    }
}
