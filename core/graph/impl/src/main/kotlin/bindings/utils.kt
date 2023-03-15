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

import com.yandex.yatagan.base.intersects
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AliasBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.ExtensibleBinding
import com.yandex.yatagan.core.graph.impl.and
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.ScopeModel

internal fun Binding.graphConditionScope(): ConditionScope = conditionScope and owner.conditionScope

internal fun BindingGraph.resolveAliasChain(node: NodeModel): List<AliasBinding> = buildList {
    var maybeAlias = resolveBindingRaw(node)
    while (maybeAlias is AliasBinding) {
        add(maybeAlias)
        maybeAlias = resolveBindingRaw(maybeAlias.source)
    }
}

internal fun ExtensibleBinding<*>.extensibleAwareDependencies(
    baseDependencies: Sequence<NodeDependency>,
): Sequence<NodeDependency> {
    return upstream?.let { baseDependencies + it.targetForDownstream } ?: baseDependencies
}

internal fun BindingGraph.canHost(bindingScopes: Set<ScopeModel>): Boolean {
    if (bindingScopes.isEmpty()) {
        return true
    }

    if (ScopeModel.Reusable in bindingScopes) {
        return true
    }

    return bindingScopes intersects scopes
}