package com.yandex.yatagan.instrumentation

public interface Statement {
    public fun <R> accept(visitor: Visitor<R>): R

    public data class Evaluate(val expression: Expression) : Statement {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitEvaluate(this)
    }

    public data class Assignment(val name: String, val value: Expression) : Statement {
        override fun <R> accept(visitor: Visitor<R>): R = visitor.visitAssignment(this)
    }

    public interface Visitor<R> {
        public fun visitEvaluate(evaluate: Evaluate): R
        public fun visitAssignment(assignment: Assignment): R
    }
}
