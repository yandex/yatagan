package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.isEager
import com.yandex.daggerlite.graph.BindingGraph

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

        if (remainingNodes.remove(node)) {
            result += node
        }
    }

    for (node in nodes) {
        visit(node)
    }

    check(remainingNodes.isEmpty()) { "Not reached" }
    return result
}