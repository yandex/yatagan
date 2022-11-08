package com.yandex.yatagan.core.graph.bindings

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Represents a way to provide a [NodeModel].
 * Each [NodeModel] must have a single [BaseBinding] for a [BindingGraph] to be valid.
 */
interface BaseBinding : MayBeInvalid, Comparable<BaseBinding> {
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
    val originModule: ModuleModel?

    fun <R> accept(visitor: Visitor<R>): R

    interface Visitor<R> {
        fun visitAlias(alias: AliasBinding): R
        fun visitBinding(binding: Binding): R
    }
}