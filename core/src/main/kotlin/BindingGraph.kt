package com.yandex.daggerlite.core

interface BindingGraph {
    /**
     * Component for which graph is built
     */
    val model: ComponentModel

    /**
     * Requested bindings that belong to this component.
     * Consists of bindings directly requested by entryPoints plus bindings requested by sub-graphs.
     * TODO: doc
     */
    val localBindings: Map<Binding, BindingUsage>

    /**
     * TODO: doc
     */
    val localConditionLiterals: Set<ConditionScope.Literal>

    /**
     * Nodes that have no binding for them.
     * Generally the graph is invalid if these are not empty. Use for error reporting.
     */
    val missingBindings: Map<NodeModel, List<NodeRequester>>

    /**
     * Child graphs (or Subcomponents). Empty if no children present.
     */
    val children: Collection<BindingGraph>

    /**
     * TODO: doc
     */
    val usedParents: Collection<BindingGraph>

    /**
     * Graph variant (full - merged with parents)
     *
     * @see ComponentModel.variant
     */
    val variant: Variant

    /**
     * Parent component, or super-component, if present.
     *
     * @see ComponentModel.isRoot
     */
    val parent: BindingGraph?

    /**
     * Resolves binding for the given node. Resulting binding may belong to this graph or any parent one.
     *
     * @return resolved binding with a graph to which it's a local binding.
     * @throws MissingBindingException if binding is not found
     */
    fun resolveBinding(node: NodeModel): Binding

    sealed interface NodeRequester {
        class EntryPointRequester(val entryPoint: ComponentModel.EntryPoint) : NodeRequester
        class BindingRequester(val binding: BaseBinding): NodeRequester
    }

    interface BindingUsage {
        val direct: Int
        val provider: Int
        val lazy: Int
        val optional: Int
        val optionalLazy: Int
        val optionalProvider: Int
    }
}
