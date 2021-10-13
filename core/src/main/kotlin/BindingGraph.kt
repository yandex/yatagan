package com.yandex.dagger3.core

import java.util.Deque
import java.util.LinkedList

class BindingGraph(
    val root: ComponentModel,
) {
    private val graphBindings = hashMapOf<NodeModel, Binding?>()

    fun resolve(node: NodeModel): Binding? {
        return graphBindings.getOrPut(node) {
            node.defaultBinding
        }
    }

    fun resolveReachable(): Map<NodeModel, Binding?> {
        return buildMap {
            val stack: Deque<NodeModel> = LinkedList()
            root.entryPoints.forEach { entryPoint ->
                stack.add(entryPoint.dep.node)
            }
            while (stack.isNotEmpty()) {
                val node = stack.pop()
                val binding = resolve(node)
                this[node] = binding
                binding?.dependencies()?.forEach { dependency ->
                    stack += dependency.node
                }
            }
        }
    }

    init {
        sequenceOf(
            root.modules.asSequence().flatMap(ModuleModel::bindings),
            root.factory?.inputs?.asSequence()?.filterIsInstance<InstanceBinding>() ?: emptySequence()
        ).flatten().associateByTo(graphBindings, Binding::target)
    }
}