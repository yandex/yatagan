package com.yandex.yatagan.core.graph.impl.bindings

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AliasBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.ExtensibleBinding
import com.yandex.yatagan.core.graph.impl.and
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel

internal fun Binding.graphConditionScope(): ConditionScope = conditionScope and owner.conditionScope

internal fun BindingGraph.resolveAliasChain(node: NodeModel): List<AliasBinding> = buildList {
    var maybeAlias = resolveBindingRaw(node)
    while (maybeAlias is AliasBinding) {
        add(maybeAlias)
        maybeAlias = resolveBindingRaw(maybeAlias.source)
    }
}

internal fun ExtensibleBinding<*>.extensibleAwareDependencies(
    baseDependencies: Sequence<NodeDependency>,
): Sequence<NodeDependency> {
    return upstream?.let { baseDependencies + it.targetForDownstream } ?: baseDependencies
}