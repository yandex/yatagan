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

/**
 * Models Boolean Algebra formulas.
 *
 * Use [accept] to work with concrete expressions.
 */
public interface BooleanExpression {
    public fun <R> accept(visitor: Visitor<R>): R

    public interface Visitor<R> {
        public fun visitVariable(variable: Variable): R
        public fun visitNot(not: Not): R
        public fun visitAnd(and: And): R
        public fun visitOr(or: Or): R
    }

    /**
     * Logical "not" of the [underlying] expression.
     */
    public interface Not : BooleanExpression {
        public val underlying: BooleanExpression
    }

    /**
     * Binary "and" (&&) operator.
     */
    public interface And : BooleanExpression {
        public val lhs: BooleanExpression
        public val rhs: BooleanExpression
    }

    /**
     * Binary "or" (||) operator.
     */
    public interface Or : BooleanExpression {
        public val lhs: BooleanExpression
        public val rhs: BooleanExpression
    }

    /**
     * Boolean variable. Contains [model].
     */
    public interface Variable : BooleanExpression {
        public val model: ConditionModel
    }
}