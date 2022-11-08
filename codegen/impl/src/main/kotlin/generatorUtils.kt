package com.yandex.yatagan.codegen.impl

import com.squareup.javapoet.CodeBlock
import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.isAlways
import com.yandex.yatagan.core.model.isNever

internal fun componentInstance(
    inside: BindingGraph,
    graph: BindingGraph,
    isInsideInnerClass: Boolean,
): CodeBlock {
    return buildExpression {
        if (isInsideInnerClass) {
            +"%T.this".formatCode(inside[ComponentImplClassName])
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
    isInsideInnerClass: Boolean,
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
    isInsideInnerClass: Boolean,
    underConditionBlock: CodeBuilder.() -> Unit,
) {
    if (!binding.conditionScope.isAlways) {
        if (!binding.conditionScope.isNever) {
            val expression = buildExpression {
                val gen = binding.owner[ConditionGenerator]
                gen.expression(
                    builder = this,
                    conditionScope = binding.conditionScope,
                    inside = inside,
                    isInsideInnerClass = isInsideInnerClass,
                )
            }
            controlFlow("if (%L) ".formatCode(expression)) {
                underConditionBlock()
            }
        }
    } else {
        underConditionBlock()
    }
}