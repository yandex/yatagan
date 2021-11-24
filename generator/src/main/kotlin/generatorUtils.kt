package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph

internal fun explicitComponentInstance(inside: BindingGraph, graph: BindingGraph): String? {
    return if (inside != graph) {
        Generators[inside].factoryGenerator.fieldNameFor(graph)
    } else null
}

internal fun componentInstance(inside: BindingGraph, graph: BindingGraph): String {
    return explicitComponentInstance(inside = inside, graph = graph)?.let { "this.$it" } ?: "this"
}

internal fun componentForBinding(inside: BindingGraph, binding: Binding): String {
    return componentInstance(inside = inside, graph = binding.owner)
}

internal fun Binding.generateAccess(
    builder: ExpressionBuilder,
    inside: BindingGraph,
    kind: DependencyKind = DependencyKind.Direct,
) {
    Generators[owner].accessStrategyManager.strategyFor(this).generateAccess(
        builder = builder,
        inside = inside,
        kind = kind,
    )
}
