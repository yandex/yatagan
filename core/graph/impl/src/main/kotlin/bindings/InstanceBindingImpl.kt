package com.yandex.daggerlite.core.graph.impl.bindings

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.bindings.Binding
import com.yandex.daggerlite.core.graph.bindings.InstanceBinding
import com.yandex.daggerlite.core.model.ComponentFactoryModel
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.format.modelRepresentation

internal class InstanceBindingImpl(
    override val target: NodeModel,
    override val owner: BindingGraph,
    private val origin: ComponentFactoryModel.InputModel,
) : InstanceBinding, BindingDefaultsMixin, ComparableByTargetBindingMixin {

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitInstance(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "bound-instance from",
        representation = origin,
    )
}