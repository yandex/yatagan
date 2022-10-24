package com.yandex.daggerlite.core.graph.impl.bindings

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.bindings.Binding
import com.yandex.daggerlite.core.graph.bindings.ProvisionBinding
import com.yandex.daggerlite.core.graph.impl.NonStaticConditionDependencies
import com.yandex.daggerlite.core.graph.impl.VariantMatch
import com.yandex.daggerlite.core.model.InjectConstructorModel
import com.yandex.daggerlite.core.model.NodeDependency
import com.yandex.daggerlite.core.model.isNever
import com.yandex.daggerlite.lang.Annotation
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.bindingModelRepresentation

internal class InjectConstructorProvisionBindingImpl(
    private val impl: InjectConstructorModel,
    override val owner: BindingGraph,
) : ProvisionBinding, ConditionalBindingMixin, ComparableByTargetBindingMixin {
    override val target get() = impl.asNode()
    override val originModule: Nothing? get() = null
    override val scopes: Set<Annotation> get() = impl.scopes
    override val provision get() = impl.constructor
    override val inputs: List<NodeDependency> get() = impl.inputs
    override val requiresModuleInstance: Boolean = false
    override val variantMatch: VariantMatch by lazy { VariantMatch(impl, owner.variant) }

    override val checkDependenciesConditionScope: Boolean
        get() = true

    override val dependencies by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (conditionScope.isNever) emptySequence() else impl.inputs.asSequence()
    }

    override val nonStaticConditionDependencies by lazy {
        NonStaticConditionDependencies(this@InjectConstructorProvisionBindingImpl)
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        validator.inline(impl)
    }

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitProvision(this)
    }

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = "inject-constructor",
        representation = { append(impl.constructor.constructee) },
        childContext = childContext,
    )
}