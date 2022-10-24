package com.yandex.daggerlite.core.graph.impl.bindings

import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.bindings.Binding
import com.yandex.daggerlite.core.graph.bindings.SubComponentFactoryBinding
import com.yandex.daggerlite.core.graph.impl.NonStaticConditionDependencies
import com.yandex.daggerlite.core.graph.impl.VariantMatch
import com.yandex.daggerlite.core.model.ComponentFactoryModel
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.core.model.isNever
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.format.modelRepresentation

internal class SubComponentFactoryBindingImpl(
    override val owner: BindingGraph,
    private val factory: ComponentFactoryModel,
) : SubComponentFactoryBinding, ConditionalBindingMixin, ComparableByTargetBindingMixin {
    override val target: NodeModel
        get() = factory.asNode()

    override val targetGraph: BindingGraph by lazy {
        val targetComponent = factory.createdComponent
        checkNotNull(owner.children.find { it.model == targetComponent }) {
            "Not reached: $this: Can't find child component $targetComponent among $owner's children."
        }
    }

    override val dependencies by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (conditionScope.isNever) emptySequence()
        else targetGraph.usedParents.map { it.model.asNode() }.asSequence()
    }

    override val variantMatch: VariantMatch by lazy {
        VariantMatch(factory.createdComponent, owner.variant)
    }

    override val nonStaticConditionDependencies by lazy {
        NonStaticConditionDependencies(this@SubComponentFactoryBindingImpl)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "child-component-factory",
        representation = factory,
    )

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitSubComponentFactory(this)
    }
}