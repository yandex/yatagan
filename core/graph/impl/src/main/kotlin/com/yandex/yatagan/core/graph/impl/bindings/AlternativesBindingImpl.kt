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

import com.yandex.yatagan.base.ExtensibleImpl
import com.yandex.yatagan.base.api.Extensible
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AlternativesBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.BindsBindingModel
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.bindingModelRepresentation

internal class AlternativesBindingImpl(
    override val impl: BindsBindingModel,
    override val owner: BindingGraph,
) : AlternativesBinding, BindingDefaultsMixin, ModuleHostedBindingMixin(), Extensible by ExtensibleImpl() {
    override val scopes get() = impl.scopes
    override val alternatives get() = impl.sources

    override val conditionScope: ConditionScope by lazy(LazyThreadSafetyMode.PUBLICATION) {
        alternatives.fold(ConditionScope.Never as ConditionScope) { acc, alternative ->
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