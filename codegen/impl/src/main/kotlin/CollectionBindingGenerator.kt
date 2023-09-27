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

package com.yandex.yatagan.codegen.impl

import com.yandex.yatagan.base.api.Extensible
import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.MultiBinding
import com.yandex.yatagan.core.graph.bindings.MultiBinding.ContributionType
import com.yandex.yatagan.core.model.CollectionTargetKind
import com.yandex.yatagan.core.model.NodeModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CollectionBindingGenerator @Inject constructor (
    @MethodsNamespace methodsNs: Namespace,
    private val thisGraph: BindingGraph,
    options: ComponentGenerator.Options,
) : MultiBindingGeneratorBase<MultiBinding>(
    methodsNs = methodsNs,
    options = options,
    bindings = thisGraph.localBindings.keys.filterIsInstance<MultiBinding>(),
    accessorPrefix = "manyOf",
) {
    override fun buildAccessorCode(builder: CodeBuilder, binding: MultiBinding) = with(builder) {
        when (binding.kind) {
            CollectionTargetKind.List -> {
                +"final %T c = new %T<>(${binding.contributions.size})"
                    .formatCode(binding.target.typeName(), Names.ArrayList)
            }
            CollectionTargetKind.Set -> {
                +"final %T c = new %T<>(${binding.contributions.size})"
                    .formatCode(binding.target.typeName(), Names.HashSet)
            }
        }

        binding.upstream?.let { upstream ->
            +buildExpression {
                +"c.addAll("
                upstream.generateAccess(
                    builder = this,
                    inside = thisGraph,
                    isInsideInnerClass = false,
                )
                +")"
            }
        }
        binding.contributions.forEach { (node: NodeModel, kind: ContributionType) ->
            val nodeBinding = thisGraph.resolveBinding(node)
            generateUnderCondition(
                binding = nodeBinding,
                inside = thisGraph,
                isInsideInnerClass = false,
            ) {
                +buildExpression {
                    when (kind) {
                        ContributionType.Element -> +"c.add("
                        ContributionType.Collection -> +"c.addAll("
                    }
                    nodeBinding.generateAccess(
                        builder = this,
                        inside = thisGraph,
                        isInsideInnerClass = false,
                    )
                    +")"
                }
            }
        }
        +"return c"
    }
}