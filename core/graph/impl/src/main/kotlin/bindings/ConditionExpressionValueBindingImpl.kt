/*
 * Copyright 2023 Yandex LLC
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
import com.yandex.yatagan.core.graph.bindings.ConditionExpressionValueBinding
import com.yandex.yatagan.core.graph.impl.NonStaticConditionDependencies
import com.yandex.yatagan.core.model.ConditionModel
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.InjectedConditionExpressionModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator

internal class ConditionExpressionValueBindingImpl(
    override val owner: BindingGraph,
    override val model: InjectedConditionExpressionModel,
) : ConditionExpressionValueBinding, BindingDefaultsMixin, ComparableByTargetBindingMixin {
    override val target: NodeModel
        get() = model.asNode()

    override val nonStaticConditionDependencies by lazy {
        NonStaticConditionDependencies(
            conditionScope = model.expression ?: ConditionScope.Always,
            host = this@ConditionExpressionValueBindingImpl,
        )
    }

    override val dependenciesOnConditions: List<ConditionModel>
        get() = model.expression?.allConditionModels() ?: emptyList()

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitConditionExpressionValue(this)
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        validator.inline(model)
    }

    override val nonStaticConditionProviders: Set<NodeModel>
        get() = nonStaticConditionDependencies.conditionProviders

    override fun toString(childContext: MayBeInvalid?) = model.toString(childContext)
}
