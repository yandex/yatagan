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

package com.yandex.yatagan.core.model

/**
 * Represents a boolean expression in Conjunctive Normal Form.
 *
 * @param L a [Literal] compatible type.
 */
@JvmInline
public value class ConditionExpression<out L : ConditionExpression.Literal>(
    /**
     * CNF - inner sets are `OR`-ed together into outer set; literals in inner sets (clauses) are `AND`-ed together.
     */
    public val expression: Set<Set<L>>,
) {
    /**
     * Must provide sensible identity operations ([equals]/[hashCode]).
     */
    public interface Literal {
        /**
         * Whether this literal "identity" is negated.
         */
        public val negated: Boolean

        /**
         * represents a `!` operation. The contract is, that `!!a === a`.
         *
         * @return negated literal.
         */
        public operator fun not(): Literal
    }

    public companion object {
        /**
         * Boolean function is a tautology.
         */
        public val Unscoped: ConditionExpression<Nothing> = ConditionExpression<Nothing>(emptySet())

        /**
         * Boolean function is never-satisfiable.
         */
        public val NeverScoped: ConditionExpression<Nothing> = ConditionExpression<Nothing>(setOf(emptySet()))
    }
}