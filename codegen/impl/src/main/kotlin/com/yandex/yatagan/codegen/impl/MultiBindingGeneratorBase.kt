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
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding

internal abstract class MultiBindingGeneratorBase<B : Binding>(
    private val bindings: List<B>,
    private val options: ComponentGenerator.Options,
    methodsNs: Namespace,
    accessorPrefix: String,
) : ComponentGenerator.Contributor {
    private val accessorNames: Map<B, String> = bindings.associateWith {
        methodsNs.name(
            nameModel = it.target.name,
            prefix = accessorPrefix,
        )
    }

    fun generateCreation(
        builder: ExpressionBuilder,
        binding: B,
        inside: BindingGraph,
        isInsideInnerClass: Boolean,
    ) {
        with(builder) {
            +"%L.%N()".formatCode(componentForBinding(
                inside = inside,
                binding = binding,
                isInsideInnerClass = isInsideInnerClass,
            ), accessorNames[binding]!!)
        }
    }

    override fun generate(builder: TypeSpecBuilder) = with(builder) {
        val bindings = if (options.sortMethodsForTesting) bindings.sorted() else bindings
        for (binding in bindings) {
            method(accessorNames[binding]!!) {
                modifiers(/*package-private*/)
                returnType(binding.target.typeName())
                buildAccessorCode(
                    builder = this,
                    binding = binding,
                )
            }
        }
    }

    abstract fun buildAccessorCode(
        builder: CodeBuilder,
        binding: B,
    )
}