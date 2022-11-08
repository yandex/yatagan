package com.yandex.yatagan.testing.procedural

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import com.yandex.yatagan.testing.procedural.GenerationParams.DependencyKind

/**
 * Represents a logical scope. A physical dagger scope may be associated with the logical scope.
 * Every graph node belongs to some logical scope.
 */
internal class LogicalScope(id: Long) {
    /**
     * All nodes that belong to this scope.
     */
    val nodes = mutableListOf<LogicalNode>()

    /**
     * All component dependencies that belong to this scope.
     */
    val componentDependencies = mutableListOf<ComponentDependency>()

    /**
     * Scope real class-name.
     */
    val className: ClassName = ClassName("test", "Scope_$id")
}

/**
 * A logical graph node. A graph consists of such nodes. Initially, no specific type and binding is associated with
 * the logical node; that information can be initialized later.
 */
internal class LogicalNode(
    /**
     * The scope it belongs to.
     */
    val scope: Tree<LogicalScope>,
    id: Long,
) {
    /**
     * All bindings that are available for this node.
     */
    val bindings = mutableSetOf<Binding>()

    /**
     * Logical node real class-name.
     * TODO: Compute it based on bindings, maybe externally.
     */
    val typeName: TypeName = ClassName("test", "Node_$id")
}

internal class Component(
    val scope: Tree<LogicalScope>,
    id: Long,
) {
    val entryPoints = mutableMapOf<LogicalNode, DependencyKind>()
    val memberInjectors = mutableListOf<MemberInjector>()
    val localBindings = mutableMapOf<LogicalNode, Binding>()
    val modules = Forest<Module>()

    val className = ClassName("test", "Component_$id")
    val creatorName = className.nestedClass("Creator")
}

internal class Module(clid: Long) {
    val bindings = mutableListOf<Binding>()
    val subcomponents = mutableListOf<Component>()

    val className = ClassName("test", "Module_$clid")
}

internal class ComponentDependency(
    private val clid: Long,
    val node: LogicalNode?,
) {
    val entryPoints = mutableListOf<Binding.ComponentDependencyEntryPoint>()

    val typeName: ClassName get() = node?.typeName as? ClassName ?: ClassName("test", "Dependency_${clid}")
}

internal class MemberInjector(
    clid: Long,
    val members: List<LogicalNode>,
) {
    val className = ClassName("test", "Injectee_${clid}")
}

/**
 * Represents a specific binding for a specific node in a dagger graph.
 */
internal sealed class Binding(
    /**
     * Node that this binding contributes to a graph.
     */
    val target: LogicalNode,
) {
    val usedIn = mutableSetOf<Component>()

    open val dependencies: Map<LogicalNode, DependencyKind>
        get() = emptyMap()

    class Inject(
        override var dependencies: Map<LogicalNode, DependencyKind>,
        target: LogicalNode,
    ) : Binding(target)

    class Provision(
        override val dependencies: Map<LogicalNode, DependencyKind>,
        target: LogicalNode,
    ) : Binding(target)

    class Alias(
        val source: LogicalNode,
        target: LogicalNode,
    ) : Binding(target) {
        override val dependencies: Map<LogicalNode, DependencyKind>
            get() = mapOf(source to DependencyKind.Direct)
    }

    class Instance(
        target: LogicalNode,
    ) : Binding(target)

    class ComponentDependencyInstance(
        target: LogicalNode,
    ): Binding(target)

    class ComponentDependencyEntryPoint(
        target: LogicalNode,
    ): Binding(target)

    // TODO: support other bindings.
}