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

import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.TypeName
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
) : MultiBindingGeneratorBase<MultiBinding>(
    methodsNs = methodsNs,
    bindings = thisGraph.localBindings.keys.filterIsInstance<MultiBinding>(),
    accessorPrefix = "manyOf",
) {
    override fun buildAccessorCode(builder: CodeBuilder, binding: MultiBinding) {
        val elementType = TypeName.Inferred(binding.target.type.typeArguments.first())
        val collectionType = when (binding.kind) {
            CollectionTargetKind.List -> TypeName.ArrayList(elementType)
            CollectionTargetKind.Set -> TypeName.HashSet(elementType)
        }

        builder.appendVariableDeclaration(
            type = TypeName.Inferred(binding.target.type),
            name = "c",
            mutable = false,
            initializer = {
                appendObjectCreation(
                    type = collectionType,
                    argumentCount = 1,
                    argument = { append(binding.contributions.size.toString()) },
                )
            }
        )

        binding.upstream?.let { upstream ->
            builder.appendStatement {
                append("c.addAll(")
                upstream.generateAccess(
                    builder = this,
                    inside = thisGraph,
                    isInsideInnerClass = false,
                )
                append(")")
            }
        }
        binding.contributions.forEach { (node: NodeModel, kind: ContributionType) ->
            val nodeBinding = thisGraph.resolveBinding(node)
            generateUnderCondition(
                builder = builder,
                binding = nodeBinding,
                inside = thisGraph,
                isInsideInnerClass = false,
            ) {
                appendStatement {
                    when (kind) {
                        ContributionType.Element -> append("c.add(")
                        ContributionType.Collection -> append("c.addAll(")
                    }
                    nodeBinding.generateAccess(
                        builder = this,
                        inside = thisGraph,
                        isInsideInnerClass = false,
                    )
                    append(")")
                }
            }
        }
        builder.appendReturnStatement { append("c") }
    }
}