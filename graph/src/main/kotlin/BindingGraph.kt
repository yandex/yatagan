package com.yandex.daggerlite.graph

import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.validation.MayBeInvalid

interface BindingGraph : MayBeInvalid {
    /**
     * Component for which graph is built
     */
    val model: ComponentModel

    /**
     * All modules that are included into this graph.
     */
    val modules: Collection<ModuleModel>

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
     */
    fun resolveBinding(node: NodeModel): Binding

    interface BindingUsage {
        val direct: Int
        val provider: Int
        val lazy: Int
        val optional: Int
        val optionalLazy: Int
        val optionalProvider: Int
    }
}
