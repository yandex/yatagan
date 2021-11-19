package com.yandex.daggerlite.core

/**
 * Represents a way to provide a [NodeModel].
 * Each [NodeModel] must have a single [BaseBinding] for a [BindingGraph] to be valid.
 */
sealed interface BaseBinding {
    /**
     * A node that this binding provides.
     */
    val target: NodeModel

    /**
     * A graph which hosts the binding.
     */
    val owner: BindingGraph

    /**
     * If binding came from a [ModuleModel] then this is it.
     * If it's intrinsic - `null` is returned.
     */
    val originatingModule: ModuleModel?
}