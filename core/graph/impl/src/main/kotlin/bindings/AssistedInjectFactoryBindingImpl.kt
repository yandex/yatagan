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

import com.yandex.yatagan.base.memoize
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AssistedInjectFactoryBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.AssistedInjectFactoryModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.append
import com.yandex.yatagan.validation.format.appendRichString
import com.yandex.yatagan.validation.format.bindingModelRepresentation

internal class AssistedInjectFactoryBindingImpl(
    override val owner: BindingGraph,
    override val model: AssistedInjectFactoryModel,
) : AssistedInjectFactoryBinding, BindingDefaultsMixin, ComparableByTargetBindingMixin {
    override val target: NodeModel
        get() = model.asNode()

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitAssistedInjectFactory(this)
    }

    override val dependencies by lazy(LazyThreadSafetyMode.PUBLICATION) {
        model.assistedConstructorParameters
            .asSequence()
            .filterIsInstance<AssistedInjectFactoryModel.Parameter.Injected>()
            .map { it.dependency }
            .memoize()
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        validator.inline(model)
    }

    override val checkDependenciesConditionScope get() = true

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = "assisted-factory",
        childContext = childContext,
        representation = {
            append(model.type)
            append("::")
            if (model.factoryMethod != null) {
                append(model.factoryMethod!!.name)
                append("(): ")
                if (model.assistedInjectConstructor != null) {
                    append(model.assistedInjectConstructor!!.constructee)
                } else {
                    appendRichString {
                        color = TextColor.Red
                        append("<invalid-target>")
                    }
                }
            } else {
                appendRichString {
                    color = TextColor.Red
                    append("<missing-factory-method>")
                }
            }
        },
    )
}