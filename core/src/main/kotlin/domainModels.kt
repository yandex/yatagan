package com.yandex.dagger3.core

import java.util.Deque
import java.util.EnumMap
import java.util.EnumSet
import java.util.LinkedList

data class NameModel(
    val packageName: String,
    val qualifiedName: String,
    val simpleName: String,
    val typeArguments: List<NameModel>,
) {
    override fun toString() = "$qualifiedName<${typeArguments.joinToString(",")}>"
}

sealed interface CallableNameModel

sealed interface MemberCallableNameModel : CallableNameModel {
    val ownerName: NameModel
}

data class FunctionNameModel(
    override val ownerName: NameModel,
    val function: String
) : MemberCallableNameModel {
    override fun toString() = "$ownerName.$function()"
}

data class ConstructorNameModel(
    val type: NameModel,
) : CallableNameModel {
    override fun toString() = "$type()"
}

data class PropertyNameModel(
    override val ownerName: NameModel,
    val property: String
) : MemberCallableNameModel {
    override fun toString() = "$ownerName.$property:"
}

data class EntryPointModel(
    val getter: MemberCallableNameModel,
    val dep: NodeDependency,
) {
    override fun toString() = "$getter -> $dep"
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
    val defaultBinding: Binding?
}

val Binding.isScoped get() = scope != null

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
    val scope: NodeScope?,
)

class AliasBinding(
    target: NodeModel,
    scope: NodeScope?,
    val source: NodeModel,
) : Binding(target, scope)

class ProvisionBinding(
    target: NodeModel,
    scope: NodeScope?,
    val provider: CallableNameModel,
    val params: Collection<NodeDependency>,
) : Binding(target, scope)

fun Binding.dependencies(): Collection<NodeDependency> = when (this) {
    is AliasBinding -> listOf(NodeDependency(source))
    is ProvisionBinding -> params
}

private inline fun <reified E : Enum<E>, V> mutableEnumMapOf(): EnumMap<E, V> = EnumMap(E::class.java)

private inline fun <reified E : Enum<E>> mutableEnumSetOf(): EnumSet<E> = EnumSet.noneOf(E::class.java)
private inline fun <reified E : Enum<E>> mutableEnumSetOf(e: E): EnumSet<E> = EnumSet.of(e)
private inline fun <reified E : Enum<E>> mutableEnumSetOf(e: E, vararg es: E): EnumSet<E> = EnumSet.of(e, *es)

class BindingGraph(
    val root: ComponentModel,
) {
    private val graphBindings = hashMapOf<NodeModel, Binding?>()

    fun resolve(node: NodeModel): Binding? {
        return graphBindings.getOrPut(node) {
            node.defaultBinding
        }
    }

    interface Visitor {
        fun visitEntryPoint(entryPoint: EntryPointModel)

        fun visitBinding(binding: Binding)

        fun visitMissingBinding(unsatisfied: NodeModel)
    }

    fun accept(visitor: Visitor) {
        val stack: Deque<NodeModel> = LinkedList()
        root.entryPoints.forEach { entryPoint ->
            visitor.visitEntryPoint(entryPoint)
            stack.add(entryPoint.dep.node)
        }
        while (stack.isNotEmpty()) {
            val node = stack.pop()
            val binding = resolve(node)
            if (binding != null) {
                visitor.visitBinding(binding)
                for (dependency in binding.dependencies()) {
                    stack += dependency.node
                }
            } else {
                visitor.visitMissingBinding(node)
            }
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