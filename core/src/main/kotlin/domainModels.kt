package com.yandex.dagger3.core

import java.util.Deque
import java.util.LinkedList

data class NameModel(
    val packageName: String,
    val qualifiedName: String,
    val simpleName: String,
) {
    override fun toString() = qualifiedName
}

sealed interface CallableNameModel {
    val ownerName: NameModel
    val name: String
}


data class FunctionNameModel(
    override val ownerName: NameModel,
    override val name: String
) : CallableNameModel {
    override fun toString() = "$ownerName.$name()"
}

data class PropertyNameModel(
    override val ownerName: NameModel,
    override val name: String
) : CallableNameModel {
    override fun toString() = "$ownerName.$name:"
}

data class EntryPointModel(
    val getterName: String,
    val dep: NodeDependency,
) {
    override fun toString() = "$getterName -> $dep"
}

/**
 * Represents @[dagger.Component] annotated class - Component.
 */
interface ComponentModel {
    val name: NameModel

    val modules: Set<ModuleModel>
    val dependencies: Set<ComponentModel>
    val entryPoints: Set<EntryPointModel>
}

interface ModuleModel {
    val bindings: Collection<Binding>
}

interface NodeQualifier
interface NodeScope

interface NodeModel {
    val name: NameModel
    val qualifier: NodeQualifier?
    val scope: NodeScope?
    val defaultBinding: Binding?
}

data class NodeDependency(
    val node: NodeModel,
    val kind: Kind = Kind.Normal,
) {
    enum class Kind {
        Normal,
        Lazy,
        Provider,
    }

    override fun toString() = "$node [$kind]"

    companion object
}

sealed class Binding(
    val target: NodeModel,
)

class AliasBinding(
    target: NodeModel,
    val source: NodeModel,
) : Binding(target)

class ProvisionBinding(
    target: NodeModel,
    val provider: CallableNameModel,
    val params: Collection<NodeDependency>,
) : Binding(target)

fun Binding.dependencies(): Collection<NodeDependency> = when (this) {
    is AliasBinding -> listOf(NodeDependency(source))
    is ProvisionBinding -> params
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