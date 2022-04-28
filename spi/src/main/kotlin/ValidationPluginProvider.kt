package com.yandex.daggerlite.spi

import com.yandex.daggerlite.graph.BindingGraph

/**
 * Service Provider Interface for [ValidationPlugin].
 *
 * @see create
 */
interface ValidationPluginProvider {
    /**
     * Creates [ValidationPlugin] for a given [root].
     * Will be called for every [BindingGraph] in all hierarchies under processing.
     *
     * @param graph a [BindingGraph] for inspection.
     */
    fun create(graph: BindingGraph): ValidationPlugin
}