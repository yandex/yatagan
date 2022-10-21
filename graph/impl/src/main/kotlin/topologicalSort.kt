package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.component1
import com.yandex.daggerlite.core.component2
import com.yandex.daggerlite.core.isEager
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph

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
