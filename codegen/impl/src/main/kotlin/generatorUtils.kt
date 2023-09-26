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

import com.yandex.yatagan.codegen.poetry.ClassName
import com.yandex.yatagan.codegen.poetry.CodeBuilder
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.codegen.poetry.nestedClass
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.lang.compiled.ClassNameModel
import com.yandex.yatagan.lang.compiled.ParameterizedNameModel

internal fun appendComponentInstance(
    builder: ExpressionBuilder,
    inside: BindingGraph,
    graph: BindingGraph,
    isInsideInnerClass: Boolean,
) {
    if (isInsideInnerClass) {
        builder.appendExplicitThis(inside[GeneratorComponent].implementationClassName)
    } else {
        builder.append("this")
    }
    if (inside != graph) {
        builder.append(".").appendName(
            inside[GeneratorComponent].componentFactoryGenerator.fieldNameFor(graph = graph)
        )
    }
}

internal fun appendComponentForBinding(
    builder: ExpressionBuilder,
    inside: BindingGraph,
    binding: Binding,
    isInsideInnerClass: Boolean,
) {
    appendComponentInstance(
        builder = builder,
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

internal fun generateUnderCondition(
    builder: CodeBuilder,
    binding: Binding,
    inside: BindingGraph,
    isInsideInnerClass: Boolean,
    underConditionBlock: CodeBuilder.() -> Unit,
) {
    when(val conditionScope = binding.conditionScope) {
        ConditionScope.Always -> builder.underConditionBlock()
        ConditionScope.Never -> {}
        is ConditionScope.ExpressionScope -> {
            builder.appendIfControlFlow(
                condition = {
                    val gen = binding.owner[GeneratorComponent].conditionGenerator
                    gen.expression(
                        builder = this,
                        conditionScope = conditionScope,
                        inside = inside,
                        isInsideInnerClass = isInsideInnerClass,
                    )
                },
                ifTrue = underConditionBlock,
            )
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
            ClassName(
                packageName = name.packageName,
                simpleNames = listOf("Yatagan$" + name.simpleNames.joinToString(separator = "$")),
            )
        }
        else -> with(parent[GeneratorComponent]) {
            implementationClassName.nestedClass(
                subcomponentsNamespace.name(graph.model.name, suffix = "Impl", firstCapital = true)
            )
        }
    }
}

internal fun typeByDependencyKind(
    kind: DependencyKind,
    type: TypeName,
) : TypeName {
    return when(kind) {
        DependencyKind.Direct -> type
        DependencyKind.Lazy -> TypeName.Lazy(type)
        DependencyKind.Provider -> TypeName.Provider(type)
        DependencyKind.Optional -> TypeName.Optional(type)
        DependencyKind.OptionalLazy -> TypeName.Optional(TypeName.Lazy(type))
        DependencyKind.OptionalProvider -> TypeName.Optional(TypeName.Provider(type))
    }
}