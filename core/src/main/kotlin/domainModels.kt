package com.yandex.dagger3.core

data class NameModel(
    val packageName: String,
    val name: String,
)

/**
 * Represents @[dagger.Component] annotated class - Component.
 */
interface ComponentModel {
    val name: NameModel

    val modules: Set<ModuleModel>
    val dependencies: Set<ComponentModel>
    val entryPoints: Set<Pair<String, NodeModel>>
}

interface ModuleModel {
    val bindings: Collection<Binding>
}

interface NodeQualifier

interface NodeModel {
    val name: NameModel
    val qualifier: NodeQualifier?
    val defaultBinding: Binding?
}

interface Binding {
    val target: NodeModel
    val dependencies: Set<NodeModel>
}

class BindingGraph(
    val root: ComponentModel,
) {
    private val graphBindings = hashMapOf<NodeModel, Binding?>()

    fun resolve(node: NodeModel): Binding? {
        return graphBindings.getOrPut(node) {
            node.defaultBinding
        }
    }

    init {
        root.modules.forEach { module ->
            for (binding in module.bindings) {
                if (binding.target in graphBindings) {
                    // Bad - duplicate binding
                    continue
                }
                graphBindings[binding.target] = binding
            }
        }
    }
}