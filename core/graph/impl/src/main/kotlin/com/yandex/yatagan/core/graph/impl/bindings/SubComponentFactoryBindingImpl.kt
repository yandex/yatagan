/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.core.graph.impl.bindings

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.SubComponentFactoryBinding
import com.yandex.yatagan.core.graph.impl.NonStaticConditionDependencies
import com.yandex.yatagan.core.graph.impl.VariantMatch
import com.yandex.yatagan.core.model.ComponentFactoryWithBuilderModel
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.format.modelRepresentation

internal class SubComponentFactoryBindingImpl(
    override val owner: BindingGraph,
    private val factory: ComponentFactoryWithBuilderModel,
) : SubComponentFactoryBinding, ConditionalBindingMixin, ComparableByTargetBindingMixin, IntrinsicBindingMarker {
    override val target: NodeModel
        get() = factory.asNode()

    override val targetGraph: BindingGraph by lazy {
        val targetComponent = factory.createdComponent
        checkNotNull(owner.children.find { it.model == targetComponent }) {
            "Not reached: $this: Can't find child component $targetComponent among $owner's children."
        }
    }

    override val dependencies by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (conditionScope == ConditionScope.Never) emptyList()
        else targetGraph.usedParents.map { it.model.asNode() }
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