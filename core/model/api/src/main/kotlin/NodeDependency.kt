package com.yandex.daggerlite.core.model

import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Represents a dependency on a particular [node] with the specified dependency [kind].
 */
interface NodeDependency : MayBeInvalid {
    /**
     * Requested node.
     */
    val node: NodeModel

    /**
     * The way the node is to be served.
     */
    val kind: DependencyKind

    /**
     * Replaces the node while preserving the [kind].
     *
     * @return a copy with the replaced [node] property.
     */
    fun copyDependency(
        node: NodeModel = this.node,
        kind: DependencyKind = this.kind,
    ): NodeDependency
}