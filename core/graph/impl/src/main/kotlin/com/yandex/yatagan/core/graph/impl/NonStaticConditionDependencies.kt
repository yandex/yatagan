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

package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.core.graph.bindings.BaseBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.impl.bindings.ConditionalBindingMixin
import com.yandex.yatagan.core.graph.impl.bindings.graphConditionScope
import com.yandex.yatagan.core.graph.impl.bindings.resolveAliasChain
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.notImplies
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.TextColor
import com.yandex.yatagan.validation.format.appendChildContextReference
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError

/**
 * See [com.yandex.yatagan.core.graph.bindings.Binding.nonStaticConditionProviders].
 */
internal interface NonStaticConditionDependencies : MayBeInvalid {
    val conditionProviders: Set<NodeModel>
}

/**
 * Constructor function for [NonStaticConditionDependencies] based on [ConditionalBindingMixin].
 */
internal fun NonStaticConditionDependencies(binding: ConditionalBindingMixin) : NonStaticConditionDependencies {
    return NonStaticConditionDependencies(
        conditionScope = binding.conditionScope,
        host = binding,
    )
}

/**
 * Constructor function for [NonStaticConditionDependencies].
 */
internal fun NonStaticConditionDependencies(
    conditionScope: ConditionScope,
    host: Binding,
): NonStaticConditionDependencies {
    val allConditionModels = conditionScope.allConditionModels()
    if (allConditionModels.isEmpty()) {
        return EmptyNonStaticConditionDependencies
    }

    val conditionProviders = allConditionModels
        .mapNotNullTo(mutableSetOf()) { model -> model.root.takeIf { model.requiresInstance } }

    return if (conditionProviders.isEmpty()) {
        EmptyNonStaticConditionDependencies
    } else {
        NonStaticConditionDependenciesImpl(
            conditionProviders = conditionProviders,
            conditionScope = conditionScope,
            host = host,
        )
    }
}

private object EmptyNonStaticConditionDependencies : NonStaticConditionDependencies {
    override val conditionProviders get() = emptySet<Nothing>()
    override fun validate(validator: Validator) = Unit
    override fun toString(childContext: MayBeInvalid?) = ""
}

private class NonStaticConditionDependenciesImpl(
    override val conditionProviders: Set<NodeModel>,
    private val host: Binding,
    private val conditionScope: ConditionScope,
) : NonStaticConditionDependencies {

    override fun validate(validator: Validator) {
        for (conditionProvider in conditionProviders) {
            validator.child(host.owner.resolveBindingRaw(conditionProvider))
        }

        val conditionScope = host.graphConditionScope()
        for (node in conditionProviders) {
            val resolved = host.owner.resolveBinding(node)
            val resolvedScope = resolved.graphConditionScope()
            if (conditionScope notImplies resolvedScope) {
                // Incompatible condition!
                validator.reportError(Strings.Errors.incompatibleConditionForConditionProvider(
                    aCondition = resolvedScope,
                    bCondition = conditionScope,
                    a = resolved,
                    b = host,
                )) {
                    val aliases = host.owner.resolveAliasChain(node)
                    if (aliases.isNotEmpty()) {
                        addNote(Strings.Notes.conditionPassedThroughAliasChain(aliases = aliases))
                    }
                }
            }
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "its non-static conditions",
        representation = {
            color = TextColor.Gray
            if (childContext is BaseBinding) {
                append("{ ")
                var count = 0
                for (model in conditionScope.allConditionModels()) {
                    if (model.requiresInstance) {
                        ++count
                        if (model.root == childContext.target) {
                            appendChildContextReference(model)
                        }
                    }
                }
                if (count > 1) {
                    append(", ..")
                }
                append(" }")
            } else {
                append(" { .. }")
            }
        }
    )
}