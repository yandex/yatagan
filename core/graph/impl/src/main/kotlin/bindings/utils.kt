package com.yandex.daggerlite.core.graph.impl.bindings

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.bindings.AliasBinding
import com.yandex.daggerlite.core.graph.bindings.Binding
import com.yandex.daggerlite.core.graph.bindings.ExtensibleBinding
import com.yandex.daggerlite.core.graph.impl.and
import com.yandex.daggerlite.core.model.ConditionScope
import com.yandex.daggerlite.core.model.NodeDependency
import com.yandex.daggerlite.core.model.NodeModel

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