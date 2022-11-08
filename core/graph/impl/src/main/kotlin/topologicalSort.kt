package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import com.yandex.yatagan.core.model.isEager

internal fun topologicalSort(
    nodes: Collection<NodeModel>,
    inside: BindingGraph,
): Collection<NodeModel> {
    // MAYBE: take non-eager edges into account yet fall back if cycles are found.

    val result = mutableListOf<NodeModel>()
    val remainingNodes = HashSet(nodes)
    val seenNodes = HashSet<NodeModel>()

    fun visit(binding: Binding) {
        // It's important to use non-alias binding here to work with real dependencies for topological sorting

        if (remainingNodes.isEmpty()) {
            // No more work to do.
            return
        }

        for (dependency in binding.dependencies) {
            val (dependencyNode, kind) = dependency
            if (!kind.isEager) {
                continue
            }

            if (!seenNodes.add(dependencyNode)) {
                // graph may be invalid and contain loops
                continue
            }

            visit(binding.owner.resolveBinding(dependencyNode))
        }

        val node = binding.target
        if (remainingNodes.remove(node)) {
            result += node
        }
    }

    // Ensure nodes are sorted in a stable order - use binding/alias ordering
    val bindings = nodes.mapTo(mutableListOf(), inside::resolveBinding)
    bindings.sort()

    for (binding in bindings) visit(binding)

    check(remainingNodes.isEmpty()) { "Not reached" }
    return result
}
