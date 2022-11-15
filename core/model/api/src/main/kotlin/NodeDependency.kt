package com.yandex.yatagan.core.model

import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Represents a dependency on a particular [node] with the specified dependency [kind].
 */
public interface NodeDependency : MayBeInvalid {
    /**
     * Requested node.
     */
    public val node: NodeModel

    /**
     * The way the node is to be served.
     */
    public val kind: DependencyKind

    /**
     * Replaces the node while preserving the [kind].
     *
     * @return a copy with the replaced [node] property.
     */
    public fun copyDependency(
        node: NodeModel = this.node,
        kind: DependencyKind = this.kind,
    ): NodeDependency
}