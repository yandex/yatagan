package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.component1
import com.yandex.daggerlite.core.component2
import com.yandex.daggerlite.core.isEager
import com.yandex.daggerlite.graph.BindingGraph

internal fun topologicalSort(
    nodes: Collection<NodeModel>,
    inside: BindingGraph,
): Collection<NodeModel> {
    // MAYBE: take non-eager edges into account yet fall back if cycles are found.

    val result = mutableListOf<NodeModel>()
    val remainingNodes = HashSet(nodes)
    val seenNodes = HashSet<NodeModel>()

    fun visit(node: NodeModel) {
        if (remainingNodes.isEmpty()) {
            // No more work to do.
            return
        }

        val binding = inside.resolveBinding(node)
        for (dependency in binding.dependencies) {
            val (dependencyNode, kind) = dependency
            if (!kind.isEager) {
                continue
            }

            if (!seenNodes.add(dependencyNode)) {
                // graph may be invalid and contain loops
                continue
            }

            visit(dependencyNode)
        }

        if (remainingNodes.remove(node)) {
            result += node
        }
    }

    for (node in nodes.sorted()) {
        visit(node)
    }

    check(remainingNodes.isEmpty()) { "Not reached" }
    return result
}
