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
import com.yandex.yatagan.core.graph.bindings.SubComponentBinding
import com.yandex.yatagan.core.graph.impl.NonStaticConditionDependencies
import com.yandex.yatagan.core.graph.impl.VariantMatch
import com.yandex.yatagan.core.model.ComponentModel
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.format.modelRepresentation

internal class SubComponentBindingImpl(
    override val owner: BindingGraph,
    private val targetComponent: ComponentModel,
) : SubComponentBinding, ConditionalBindingMixin, ComparableByTargetBindingMixin, IntrinsicBindingMarker {
    init {
        require(targetComponent.factory == null)
    }

    override val target: NodeModel
        get() = targetComponent.asNode()

    override val targetGraph: BindingGraph by lazy {
        checkNotNull(owner.children.find { it.model == targetComponent }) {
            "Not reached: $this: Can't find child component $targetComponent among $owner's children."
        }
    }

    override val dependencies by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (conditionScope == ConditionScope.Never) emptySequence()
        else targetGraph.usedParents.map { it.model.asNode() }.asSequence()
    }

    override val variantMatch: VariantMatch by lazy {
        VariantMatch(targetComponent, owner.variant)
    }

    override val nonStaticConditionDependencies by lazy {
        NonStaticConditionDependencies(this@SubComponentBindingImpl)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "child",
        representation = targetComponent,
    )

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitSubComponent(this)
    }
}