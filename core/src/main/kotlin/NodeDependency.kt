package com.yandex.daggerlite.core

/**
 * A [NodeModel] with a request [DependencyKind].
 */
data class NodeDependency(
    val node: NodeModel,
    val kind: DependencyKind = DependencyKind.Direct,
) {
    override fun toString() = "$node [$kind]"
}