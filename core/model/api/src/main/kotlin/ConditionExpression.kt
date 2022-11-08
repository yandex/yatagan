package com.yandex.yatagan.core.model

/**
 * Represents a boolean expression in Conjunctive Normal Form.
 *
 * @param L a [Literal] compatible type.
 */
@JvmInline
value class ConditionExpression<out L : ConditionExpression.Literal>(
    /**
     * CNF - inner sets are `OR`-ed together into outer set; literals in inner sets (clauses) are `AND`-ed together.
     */
    val expression: Set<Set<L>>,
) {
    /**
     * Must provide sensible identity operations ([equals]/[hashCode]).
     */
    interface Literal {
        /**
         * Whether this literal "identity" is negated.
         */
        val negated: Boolean

        /**
         * represents a `!` operation. The contract is, that `!!a === a`.
         *
         * @return negated literal.
         */
        operator fun not(): Literal
    }

    companion object {
        /**
         * Boolean function is a tautology.
         */
        val Unscoped = ConditionExpression<Nothing>(emptySet())

        /**
         * Boolean function is never-satisfiable.
         */
        val NeverScoped = ConditionExpression<Nothing>(setOf(emptySet()))
    }
}