package com.yandex.daggerlite.generator

import com.squareup.javapoet.CodeBlock
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.generator.poetry.CodeBuilder
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph

internal fun componentInstance(
    inside: BindingGraph,
    graph: BindingGraph,
    isInsideInnerClass: Boolean = false,
): CodeBlock {
    return buildExpression {
        if (isInsideInnerClass) {
            +"%T.this".formatCode(graph[ComponentImplClassName])
        } else {
            +"this"
        }
        if (inside != graph) {
            +".%N".formatCode(
                inside[ComponentFactoryGenerator].fieldNameFor(graph = graph)
            )
        }
    }
}

internal fun componentForBinding(
    inside: BindingGraph,
    binding: Binding,
    isInsideInnerClass: Boolean,
): CodeBlock {
    return componentInstance(
        inside = inside,
        graph = binding.owner,
        isInsideInnerClass = isInsideInnerClass,
    )
}

internal fun Binding.generateAccess(
    builder: ExpressionBuilder,
    inside: BindingGraph,
    isInsideInnerClass: Boolean = false,
    kind: DependencyKind = DependencyKind.Direct,
) {
    owner[AccessStrategyManager].strategyFor(this).generateAccess(
        builder = builder,
        inside = inside,
        isInsideInnerClass = isInsideInnerClass,
        kind = kind,
    )
}

internal inline fun CodeBuilder.generateUnderCondition(
    binding: Binding,
    inside: BindingGraph,
    underConditionBlock: CodeBuilder.() -> Unit,
) {
    if (!binding.conditionScope.isAlways) {
        if (!binding.conditionScope.isNever) {
            val expression = buildExpression {
                val gen = binding.owner[ConditionGenerator]
                gen.expression(this, binding.conditionScope, inside = inside)
            }
            controlFlow("if (%L) ".formatCode(expression)) {
                underConditionBlock()
            }
        }
    } else {
        underConditionBlock()
    }
}