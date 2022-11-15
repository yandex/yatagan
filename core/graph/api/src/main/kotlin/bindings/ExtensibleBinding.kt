package com.yandex.yatagan.core.graph.bindings

import com.yandex.yatagan.core.model.NodeModel

/**
 * A binding that can override/extend a binding with the same [target] from the parent graph.
 */
public interface ExtensibleBinding<B> : Binding where B : ExtensibleBinding<B> {
    /**
     * An optional reference to a binding from one of the parent graphs, to include contributions from.
     */
    public val upstream: B?

    /**
     * A special intrinsic node, which is used for downstream binding to depend on this binding
     *  (as its [upstream]).
     *
     * Any downstream bindings' dependencies must include this node.
     */
    public val targetForDownstream: NodeModel
}