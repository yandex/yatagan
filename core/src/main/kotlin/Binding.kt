package com.yandex.dagger3.core

/**
 * Represents a way to provide a [NodeModel].
 * Each [NodeModel] must have a single [Binding] for a [BindingGraph] to be valid.
 */
sealed interface Binding {
    /**
     * A node that this binding provides.
     */
    val target: NodeModel

}