package com.yandex.yatagan.core.graph.impl.bindings

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyEntryPointBinding
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.format.modelRepresentation

internal class ComponentDependencyEntryPointBindingImpl(
    override val owner: BindingGraph,
    override val dependency: ComponentDependencyModel,
    override val getter: Method,
    override val target: NodeModel,
) : ComponentDependencyEntryPointBinding, BindingDefaultsMixin,
    ComparableBindingMixin<ComponentDependencyEntryPointBindingImpl> {

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitComponentDependencyEntryPoint(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-dependency-getter",
        representation = getter,
    )

    override fun compareTo(other: ComponentDependencyEntryPointBindingImpl): Int {
        return getter.compareTo(other.getter)
    }
}