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
import com.yandex.yatagan.core.graph.bindings.ProvisionBinding
import com.yandex.yatagan.core.graph.impl.NonStaticConditionDependencies
import com.yandex.yatagan.core.graph.impl.VariantMatch
import com.yandex.yatagan.core.model.ProvidesBindingModel
import com.yandex.yatagan.core.model.isNever
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.bindingModelRepresentation

internal class ProvisionBindingImpl(
    override val impl: ProvidesBindingModel,
    override val owner: BindingGraph,
) : ProvisionBinding, ConditionalBindingMixin, ModuleHostedBindingMixin() {

    override val scopes get() = impl.scopes
    override val provision get() = impl.method
    override val inputs get() = impl.inputs
    override val requiresModuleInstance get() = impl.requiresModuleInstance
    override val variantMatch: VariantMatch by lazy { VariantMatch(impl, owner.variant) }

    override val dependencies by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (conditionScope.isNever) emptySequence() else inputs.asSequence()
    }

    override val nonStaticConditionDependencies by lazy {
        NonStaticConditionDependencies(this@ProvisionBindingImpl)
    }

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = "provision",
        childContext = childContext,
        representation = {
            append(impl.originModule.type)
            append("::")
            append(impl.method.name)
        },
    )

    override val checkDependenciesConditionScope get() = true

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitProvision(this)
    }
}