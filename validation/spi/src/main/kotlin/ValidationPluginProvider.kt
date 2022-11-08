package com.yandex.yatagan.validation.spi

import com.yandex.yatagan.core.graph.BindingGraph

/**
 * Service Provider Interface for [ValidationPlugin].
 *
 * @see create
 */
interface ValidationPluginProvider {
    /**
     * Creates [ValidationPlugin] for a given [graph].
     * Will be called for every [BindingGraph] in all hierarchies under processing.
     *
     * @param graph a [BindingGraph] for inspection.
     */
    fun create(graph: BindingGraph): ValidationPlugin
}