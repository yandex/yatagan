package com.yandex.yatagan.core.graph.impl.bindings

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AliasBinding
import com.yandex.yatagan.core.graph.bindings.BaseBinding
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator

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