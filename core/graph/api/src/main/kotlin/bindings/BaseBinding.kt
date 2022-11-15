package com.yandex.yatagan.core.graph.bindings

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Represents a way to provide a [NodeModel].
 * Each [NodeModel] must have a single [BaseBinding] for a [BindingGraph] to be valid.
 */
public interface BaseBinding : MayBeInvalid, Comparable<BaseBinding> {
    /**
     * A node that this binding provides.
     */
    public val target: NodeModel

    /**
     * A graph which hosts the binding.
     */
    public val owner: BindingGraph

    /**
     * If binding came from a [ModuleModel] then this is it.
     * If it's intrinsic - `null` is returned.
     */
    public val originModule: ModuleModel?

    public fun <R> accept(visitor: Visitor<R>): R

    public interface Visitor<R> {
        public fun visitAlias(alias: AliasBinding): R
        public fun visitBinding(binding: Binding): R
    }
}