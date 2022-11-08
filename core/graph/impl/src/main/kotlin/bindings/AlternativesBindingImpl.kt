package com.yandex.yatagan.core.graph.impl.bindings

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AlternativesBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.impl.or
import com.yandex.yatagan.core.model.BindsBindingModel
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.bindingModelRepresentation

internal class AlternativesBindingImpl(
    override val impl: BindsBindingModel,
    override val owner: BindingGraph,
) : AlternativesBinding, BindingDefaultsMixin, ModuleHostedBindingMixin() {
    override val scopes get() = impl.scopes
    override val alternatives get() = impl.sources

    override val conditionScope: ConditionScope by lazy(LazyThreadSafetyMode.PUBLICATION) {
        alternatives.fold(ConditionScope.NeverScoped as ConditionScope) { acc, alternative ->
            val binding = owner.resolveBinding(alternative)
            acc or binding.conditionScope
        }
    }

    override val dependencies get() = alternatives

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = "alias-with-alternatives",
        childContext = childContext,
        representation = { append(impl.originModule.type).append("::").append(impl.method.name) },
    )

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitAlternatives(this)
    }

    // TODO: issue warnings about unreachable alternatives
}