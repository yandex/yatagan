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

package com.yandex.yatagan.core.model

import com.yandex.yatagan.base.api.Incubating
import com.yandex.yatagan.core.model.ConditionScope.Always
import com.yandex.yatagan.core.model.ConditionScope.ExpressionScope
import com.yandex.yatagan.core.model.ConditionScope.Never

/**
 * A condition "scope" of the binding - contains knowledge on whether the binding is present or absent from the graph
 * and how to compute that. Maybe [Always], [Never] or [ExpressionScope].
 */
@Incubating
public sealed interface ConditionScope {

    /**
     * Whether this is an [Always]-scope or an [ExpressionScope] that is a tautology - always evaluates to `true`.
     */
    public fun isTautology(): Boolean

    /**
     * Whether this is a [Never]-scope or an [ExpressionScope] that is a contradiction - never evaluates to `true`.
     */
    public fun isContradiction(): Boolean

    /**
     * Checks `A |= B`, where `A` is `this`, and `B` is [another].
     *
     * @return `true` if every valuation that causes `this` to be true also causes [another] to be true.
     *  `false` otherwise.
     */
    public infix fun implies(another: ConditionScope): Boolean

    /**
     * Performs an "AND" operation on scopes.
     * @return (`this` && [another]) scope.
     */
    public infix fun and(another: ConditionScope): ConditionScope

    /**
     * Performs an "OR" operation on scopes.
     * @return (`this` || [another]) scope.
     */
    public infix fun or(another: ConditionScope): ConditionScope

    /**
     * Performs a "NOT" operation on this scope.
     * @return `!this` scope.
     */
    public operator fun not(): ConditionScope

    /**
     * Enumerates all condition models, contained in the scope in order of their appearance in the expression.
     */
    public fun allConditionModels(): List<ConditionModel>

    /**
     * Denotes a special *explicit* "Always" scope, that is effectively a `true` constant.
     * E.g. every binding without any specified condition has this condition scope.
     */
    @Incubating
    public data object Always : ConditionScope {
        override fun isTautology(): Boolean = true
        override fun isContradiction(): Boolean = false
        override fun implies(another: ConditionScope): Boolean = another.isTautology()
        override fun and(another: ConditionScope): ConditionScope = another
        override fun or(another: ConditionScope): ConditionScope = Always
        override fun not(): ConditionScope = Never
        override fun allConditionModels(): List<Nothing> = emptyList()
    }

    /**
     * Denotes a special *explicit* "Never" scope, that is effectively a `false` constant.
     * E.g. explicitly
     */
    @Incubating
    public data object Never : ConditionScope {
        override fun isTautology(): Boolean = false
        override fun isContradiction(): Boolean = true
        override fun implies(another: ConditionScope): Boolean = true
        override fun and(another: ConditionScope): ConditionScope = Never
        override fun or(another: ConditionScope): ConditionScope = another
        override fun not(): ConditionScope = Always
        override fun allConditionModels(): List<Nothing> = emptyList()
    }

    /**
     * Denotes a complex condition scope with the boolean [expression].
     * Boolean expression can be evaluated in graph runtime (generated or reflected).
     */
    @Incubating
    public interface ExpressionScope : ConditionScope {
        /**
         * Boolean formula that expresses the condition scope.
         */
        public val expression: BooleanExpression
    }
}