package com.yandex.daggerlite.core.graph.impl.bindings

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.bindings.Binding
import com.yandex.daggerlite.core.graph.bindings.EmptyBinding
import com.yandex.daggerlite.core.model.ConditionScope
import com.yandex.daggerlite.core.model.ModuleHostedBindingModel
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.format.modelRepresentation

internal class ExplicitEmptyBindingImpl(
    override val impl: ModuleHostedBindingModel,
    override val owner: BindingGraph,
) : EmptyBinding, BindingDefaultsMixin, ModuleHostedBindingMixin() {
    override val conditionScope get() = ConditionScope.NeverScoped

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "explicit-absent by",
        representation = impl,
    )
}