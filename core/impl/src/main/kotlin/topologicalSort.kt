package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.isEager

internal fun topologicalSort(
    nodes: Collection<NodeModel>,
    inside: BindingGraph,
): Collection<NodeModel> {
    // MAYBE: take non-eager edges into account yet fall back if cycles are found.

    val result = mutableListOf<NodeModel>()
    val remainingNodes = HashSet(nodes)

    fun visit(node: NodeModel) {
        if (remainingNodes.isEmpty()) {
            // No more work to do.
            return
        }

        val binding = inside.resolveBinding(node)
        for (dependency in binding.dependencies()) {
            if (dependency.kind.isEager) {
                visit(dependency.node)
            }
        }

        if (node in remainingNodes) {
            result += node
            remainingNodes -= node
        }
    }

    for (node in nodes) {
        visit(node)
    }

    return result
}