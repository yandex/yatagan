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