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
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.InjectConstructorModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.bindingModelRepresentation

internal class InjectConstructorProvisionBindingImpl(
    private val impl: InjectConstructorModel,
    override val owner: BindingGraph,
) : ProvisionBinding, ConditionalBindingMixin, ComparableByTargetBindingMixin {
    override val target get() = impl.asNode()
    override val originModule: Nothing? get() = null
    override val scopes get() = impl.scopes
    override val provision get() = impl.constructor
    override val inputs: List<NodeDependency> get() = impl.inputs
    override val requiresModuleInstance: Boolean = false
    override val variantMatch: VariantMatch by lazy { VariantMatch(impl, owner.variant) }

    override val checkDependenciesConditionScope: Boolean
        get() = true

    override val dependencies by lazy(LazyThreadSafetyMode.PUBLICATION) {
        if (conditionScope == ConditionScope.Never) emptyList() else impl.inputs
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