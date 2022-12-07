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

import com.squareup.javapoet.CodeBlock
import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.isAlways
import com.yandex.yatagan.core.model.isNever

internal fun componentInstance(
    inside: BindingGraph,
    graph: BindingGraph,
    isInsideInnerClass: Boolean,
): CodeBlock {
    return buildExpression {
        if (isInsideInnerClass) {
            +"%T.this".formatCode(inside[ComponentImplClassName])
        } else {
            +"this"
        }
        if (inside != graph) {
            +".%N".formatCode(
                inside[ComponentFactoryGenerator].fieldNameFor(graph = graph)
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
    owner[AccessStrategyManager].strategyFor(this).generateAccess(
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
    if (!binding.conditionScope.isAlways) {
        if (!binding.conditionScope.isNever) {
            val expression = buildExpression {
                val gen = binding.owner[ConditionGenerator]
                gen.expression(
                    builder = this,
                    conditionScope = binding.conditionScope,
                    inside = inside,
                    isInsideInnerClass = isInsideInnerClass,
                )
            }
            controlFlow("if (%L) ".formatCode(expression)) {
                underConditionBlock()
            }
        }
    } else {
        underConditionBlock()
    }
}