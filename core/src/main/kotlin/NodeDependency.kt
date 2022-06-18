package com.yandex.daggerlite.core

/**
 * Represents a dependency on a particular [node] with the specified dependency [kind].
 */
interface NodeDependency {
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
    fun replaceNode(node: NodeModel): NodeDependency
}