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

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.lang.compiled.ClassNameModel
import com.yandex.yatagan.lang.compiled.ParameterizedNameModel

internal fun componentInstance(
    inside: BindingGraph,
    graph: BindingGraph,
    isInsideInnerClass: Boolean,
): CodeBlock {
    return buildExpression {
        if (isInsideInnerClass) {
            +"%T.this".formatCode(inside[GeneratorComponent].implementationClassName)
        } else {
            +"this"
        }
        if (inside != graph) {
            +".%N".formatCode(
                inside[GeneratorComponent].componentFactoryGenerator.fieldNameFor(graph = graph)
            )
        }
    }
}

internal fun componentForBinding(
    inside: BindingGraph,
    binding: Binding,
    isInsideInnerClass: Boolean,
): CodeBlock {
    return componentInstance(
        inside = inside,
        graph = binding.owner,
        isInsideInnerClass = isInsideInnerClass,
    )
}

internal fun Binding.generateAccess(
    builder: ExpressionBuilder,
    inside: BindingGraph,
    isInsideInnerClass: Boolean,
    kind: DependencyKind = DependencyKind.Direct,
) {
    owner[GeneratorComponent].accessStrategyManager.strategyFor(this).generateAccess(
        builder = builder,
        inside = inside,
        isInsideInnerClass = isInsideInnerClass,
        kind = kind,
    )
}

internal inline fun CodeBuilder.generateUnderCondition(
    binding: Binding,
    inside: BindingGraph,
    isInsideInnerClass: Boolean,
    underConditionBlock: CodeBuilder.() -> Unit,
) {
    when(val conditionScope = binding.conditionScope) {
        ConditionScope.Always -> underConditionBlock()
        ConditionScope.Never -> {}
        is ConditionScope.ExpressionScope -> {
            val expression = buildExpression {
                val gen = binding.owner[GeneratorComponent].conditionGenerator
                gen.expression(
                    builder = this,
                    conditionScope = conditionScope,
                    inside = inside,
                    isInsideInnerClass = isInsideInnerClass,
                )
            }
            controlFlow("if (%L) ".formatCode(expression)) {
                underConditionBlock()
            }
        }
    }
}

internal fun formatImplementationClassName(graph: BindingGraph): ClassName {
    return when(val parent = graph.parent) {
        null -> graph.model.name.let {
            val name = when (it) {
                is ClassNameModel -> it
                is ParameterizedNameModel -> it.raw
                else -> throw AssertionError("Unexpected component name: $it")
            }
            // Keep name mangling in sync with loader!
            ClassName.get(name.packageName, "Yatagan" + name.simpleNames.joinToString(separator = "_"))
        }
        else -> with(parent[GeneratorComponent]) {
            implementationClassName.nestedClass(
                subcomponentsNamespace.name(graph.model.name, suffix = "Impl", firstCapital = true)
            )
        }
    }
}