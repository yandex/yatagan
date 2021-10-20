package com.yandex.daggerlite.core

interface BindingGraph {
    /**
     * Component for which graph is built
     */
    val component: ComponentModel

    /**
     * Requested bindings that belong to this component.
     * Consists of bindings directly requested by entryPoints plus bindings requested by sub-graphs.
     * TODO: doc
     */
    val localBindings: Map<Binding, BindingUsage>

    /**
     * Nodes that have no binding for them.
     * Generally the graph is invalid if these are not empty. Use for error reporting.
     */
    val missingBindings: Collection<NodeModel>

    /**
     * Child graphs (or Subcomponents). Empty if no children present.
     */
    val children: Collection<BindingGraph>

    /**
     * TODO: doc
     */
    val usedParents: Collection<ComponentModel>

    /**
     * Resolves binding for the given node. Resulting binding may belong to this graph or any parent one.
     *
     * @return resolved binding with a graph to which it's a local binding.
     * @throws MissingBindingException if binding is not found
     */
    fun resolveBinding(node: NodeModel): Binding
}

/**
 * Creates [BindingGraph] instance given the root component.
 */
fun BindingGraph(root: ComponentModel): BindingGraph {
    require(root.isRoot) { "can't use non-root component as a root of a binding graph" }
    return BindingGraphImpl(root)
}

interface BindingUsage {
    val direct: Int
    val provider: Int
    val lazy: Int
}
