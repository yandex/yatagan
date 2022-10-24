package com.yandex.daggerlite.core.graph.impl.bindings

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.bindings.AliasBinding
import com.yandex.daggerlite.core.graph.bindings.BaseBinding
import com.yandex.daggerlite.core.model.ModuleModel
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator

internal class SyntheticAliasBindingImpl(
    override val source: NodeModel,
    override val target: NodeModel,
    override val owner: BindingGraph,
) : AliasBinding {
    private val sourceBinding by lazy(LazyThreadSafetyMode.PUBLICATION) { owner.resolveBindingRaw(source) }

    override val originModule: ModuleModel? get() = null
    override fun <R> accept(visitor: BaseBinding.Visitor<R>): R {
        return visitor.visitAlias(this)
    }

    override fun validate(validator: Validator) {
        validator.inline(owner.resolveBindingRaw(source))
    }

    override fun toString(childContext: MayBeInvalid?): CharSequence {
        // Pass-through
        return sourceBinding.toString(childContext)
    }

    override fun compareTo(other: BaseBinding): Int {
        // Pass-through
        return sourceBinding.compareTo(other)
    }
}