package com.yandex.yatagan.instrumentation.impl

import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.instrumentation.Expression
import com.yandex.yatagan.instrumentation.Statement

internal class InstrumentedDependenciesExtractor(
    private val dependencies: MutableSet<in NodeModel>,
) : Expression.Visitor<Unit>, Statement.Visitor<Unit> {
    override fun visitEvaluate(evaluate: Statement.Evaluate) = evaluate.expression.accept(this)
    override fun visitAssignment(assignment: Statement.Assignment) = assignment.value.accept(this)

    override fun visitMethodCall(methodCall: Expression.MethodCall) {
        methodCall.receiver?.accept(this)
        methodCall.arguments.forEach { it.accept(this) }
    }

    override fun visitResolveInstance(resolveInstance: Expression.ResolveInstance) {
        dependencies += resolveInstance.asNode()
    }

    override fun visitLiteral(literal: Expression.Literal) = Unit
    override fun visitReadValue(readValue: Expression.ReadValue) = Unit
}
