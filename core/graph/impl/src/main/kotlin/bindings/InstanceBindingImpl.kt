package com.yandex.yatagan.core.graph.impl.bindings

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.InstanceBinding
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.format.modelRepresentation

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