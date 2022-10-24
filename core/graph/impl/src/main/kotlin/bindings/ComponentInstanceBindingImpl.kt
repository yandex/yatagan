package com.yandex.daggerlite.core.graph.impl.bindings

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.bindings.Binding
import com.yandex.daggerlite.core.graph.bindings.ComponentInstanceBinding
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.format.modelRepresentation

internal class ComponentInstanceBindingImpl(
    graph: BindingGraph,
) : ComponentInstanceBinding, BindingDefaultsMixin, ComparableByTargetBindingMixin {
    override val owner: BindingGraph = graph
    override val target get() = owner.model.asNode()

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitComponentInstance(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-instance",
        representation = owner,
    )
}