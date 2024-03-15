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

import com.yandex.yatagan.base.concatOrThis
import com.yandex.yatagan.base.traverseDepthFirstWithPath
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AliasBinding
import com.yandex.yatagan.core.graph.bindings.BaseBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.component1
import com.yandex.yatagan.core.graph.component2
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import com.yandex.yatagan.core.model.isEager
import com.yandex.yatagan.instrumentation.impl.instrumentedDependencies
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.reportError

internal fun validateNoLoops(graph: BindingGraph, validator: Validator) {
    traverseDepthFirstWithPath<Pair<DependencyKind, BaseBinding>>(
        roots = buildList {
            graph.entryPoints.forEach { (_, dependency) ->
                add(dependency.kind to graph.resolveBindingRaw(dependency.node))
            }
            graph.memberInjectors.forEach {
                it.membersToInject.forEach { (_, dependency) ->
                    add(dependency.kind to graph.resolveBindingRaw(dependency.node))
                }
            }
        },
        childrenOf = { (_, binding) ->
            class DependenciesVisitor : BaseBinding.Visitor<List<NodeDependency>> {
                override fun visitOther(other: BaseBinding) = throw AssertionError()
                override fun visitAlias(alias: AliasBinding) = listOf(alias.source)
                override fun visitBinding(binding: Binding) =
                    binding.dependencies
                        .concatOrThis(binding.nonStaticConditionProviders)
                        .concatOrThis(binding.instrumentedDependencies())
            }
            binding.accept(DependenciesVisitor()).map { (node, kind) ->
                kind to binding.owner.resolveBindingRaw(node)
            }
        },
        onLoop = { bindingLoop ->
            // Check for lazy edges - if they are present, this is a "benign" loop, don't report it.
            if (bindingLoop.all { (kind, _) -> kind.isEager }) {
                val chain = bindingLoop.mapTo(arrayListOf()) { (_, binding) -> binding }
                validator.reportError(Strings.Errors.dependencyLoop(chain = chain))
            }
        }
    )
}