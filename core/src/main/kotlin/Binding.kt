package com.yandex.daggerlite.core

/**
 * Represents a way to provide a [NodeModel].
 * Each [NodeModel] must have a single [Binding] for a [BindingGraph] to be valid.
 */
sealed class Binding {
    /**
     * A node that this binding provides.
     */
    abstract val target: NodeModel

    /**
     * A graph which hosts the binding.
     * Guaranteed to be initialized only for bindings retrieved from a [BindingGraph].
     */
    var owner: BindingGraph by lateInit()
        internal set
}