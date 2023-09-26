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

import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AlternativesBinding
import com.yandex.yatagan.core.graph.bindings.AssistedInjectFactoryBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyBinding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyEntryPointBinding
import com.yandex.yatagan.core.graph.bindings.ComponentInstanceBinding
import com.yandex.yatagan.core.graph.bindings.EmptyBinding
import com.yandex.yatagan.core.graph.bindings.InstanceBinding
import com.yandex.yatagan.core.graph.bindings.MapBinding
import com.yandex.yatagan.core.graph.bindings.MultiBinding
import com.yandex.yatagan.core.graph.bindings.ProvisionBinding
import com.yandex.yatagan.core.graph.bindings.SubComponentBinding
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import com.yandex.yatagan.lang.Callable
import com.yandex.yatagan.lang.Constructor
import com.yandex.yatagan.lang.Method

private class CreationGeneratorVisitor(
    private val builder: ExpressionBuilder,
    private val inside: BindingGraph,
    private val isInsideInnerClass: Boolean,
) : Binding.Visitor<Unit> {
    override fun visitOther(binding: Binding) = throw AssertionError()

    override fun visitProvision(binding: ProvisionBinding) {
        with(builder) {
            binding.provision.accept(object : Callable.Visitor<Unit> {
                override fun visitMethod(method: Method) {
                    val enableNullChecks = inside[GeneratorComponent].options.enableProvisionNullChecks
                    val provision: ExpressionBuilder.() -> Unit = {
                        appendCall(
                            receiver = fun ExpressionBuilder.() {
                                appendComponentForBinding(builder, binding)
                                append(".").appendName(
                                    binding.owner[GeneratorComponent]
                                        .componentFactoryGenerator.fieldNameFor(binding.originModule!!)
                                )
                            }.takeIf { binding.requiresModuleInstance },
                            method = method,
                            argumentCount = binding.inputs.size,
                            argument = {
                                val (node, kind) = binding.inputs[it]
                                inside.resolveBinding(node).generateAccess(
                                    builder = this,
                                    inside = inside,
                                    kind = kind,
                                    isInsideInnerClass = isInsideInnerClass,
                                )
                            },
                        )
                    }
                    if (enableNullChecks) {
                        appendCheckProvisionNotNull {
                            provision()
                        }
                    } else {
                        provision()
                    }
                }

                override fun visitConstructor(constructor: Constructor) {
                    val parameters = constructor.parameters.toList()
                    builder.appendObjectCreation(
                        type = TypeName.Inferred(constructor.constructee.asType()),
                        argumentCount = parameters.size,
                        argument = {
                            val (node, kind) = binding.inputs[it]
                            inside.resolveBinding(node).generateAccess(
                                builder = this,
                                inside = inside,
                                kind = kind,
                                isInsideInnerClass = isInsideInnerClass,
                            )
                        },
                    )
                }

                override fun visitOther(callable: Callable) = throw AssertionError()
            })
        }
    }

    override fun visitAssistedInjectFactory(binding: AssistedInjectFactoryBinding) {
        binding.owner[GeneratorComponent].assistedInjectFactoryGenerator.generateCreation(
            builder = builder,
            binding = binding,
            inside = inside,
            isInsideInnerClass = isInsideInnerClass,
        )
    }

    override fun visitInstance(binding: InstanceBinding) {
        appendComponentForBinding(builder, binding)
        builder.append(".").appendName(
            binding.owner[GeneratorComponent].componentFactoryGenerator.fieldNameFor(binding.target)
        )
    }

    override fun visitAlternatives(binding: AlternativesBinding) {
        data class AlternativesGenerationEntry(
            val access: ExpressionBuilder.() -> Unit,
            val condition: (ExpressionBuilder.() -> Unit)?,
        )

        var scopeOfPreviousAlternatives: ConditionScope = ConditionScope.Never
        val alternatives = binding.alternatives
        val entriesToGenerate = mutableListOf<AlternativesGenerationEntry>()
        for (index in alternatives.indices) {
            val alternative = alternatives[index]
            val altBinding = inside.resolveBinding(alternative)
            val altBindingScope = altBinding.conditionScope

            // Condition: "the alternative is present" AND "none of the previous alternatives are present".
            val actualAltBindingScope = !scopeOfPreviousAlternatives and altBindingScope

            scopeOfPreviousAlternatives = scopeOfPreviousAlternatives or altBindingScope
            when {
                // non-never last-index - no need to check condition, because it's guaranteed to be present,
                // if queried - that is validated at build time.
                (index == alternatives.lastIndex && altBindingScope != ConditionScope.Never) ||
                        altBindingScope == ConditionScope.Always -> {
                    entriesToGenerate += AlternativesGenerationEntry(
                        access = {
                            altBinding.generateAccess(
                                builder = this,
                                inside = inside,
                                isInsideInnerClass = isInsideInnerClass,
                            )
                        },
                        condition = null,
                    )
                    break  // no further generation, the rest are (if any) unreachable.
                }

                altBindingScope == ConditionScope.Never || actualAltBindingScope.isContradiction() -> {
                    // Never scoped is, by definition, unreached, so just skip it.
                    continue
                }

                altBindingScope is ConditionScope.ExpressionScope -> {
                    entriesToGenerate += AlternativesGenerationEntry(
                        access = {
                            altBinding.generateAccess(
                                builder = this,
                                inside = inside,
                                isInsideInnerClass = isInsideInnerClass,
                            )
                        },
                        condition = {
                            val gen = inside[GeneratorComponent].conditionGenerator
                            gen.expression(
                                builder = this,
                                conditionScope = altBindingScope,
                                inside = inside,
                                isInsideInnerClass = isInsideInnerClass,
                            )
                        },
                    )
                }

                else -> throw AssertionError("Not reached!")
            }
        }

        fun ExpressionBuilder.generateEntry(entryIndex: Int) {
            val (access, condition) = entriesToGenerate[entryIndex]
            if (condition != null && entryIndex != entriesToGenerate.lastIndex) {
                appendTernaryExpression(
                    condition = condition,
                    ifTrue = access,
                    ifFalse = { generateEntry(entryIndex + 1) },
                )
            } else {
                access()
            }
        }
        builder.generateEntry(0)
    }

    override fun visitSubComponent(binding: SubComponentBinding) {
        val usedParents = binding.targetGraph.usedParents.toList()
        builder.appendObjectCreation(
            type = binding.targetGraph[GeneratorComponent].componentFactoryGenerator.implName,
            argumentCount = usedParents.size,
            argument = {
                appendComponentInstance(
                    builder = this,
                    inside = inside,
                    graph = usedParents[it],
                    isInsideInnerClass = isInsideInnerClass,
                )
            },
        )
    }

    override fun visitComponentDependency(binding: ComponentDependencyBinding) {
        appendComponentForBinding(builder, binding)
        builder.append(".").appendName(
            binding.owner[GeneratorComponent].componentFactoryGenerator.fieldNameFor(binding.dependency)
        )
    }

    override fun visitComponentInstance(binding: ComponentInstanceBinding) {
        appendComponentForBinding(builder, binding)
    }

    override fun visitComponentDependencyEntryPoint(binding: ComponentDependencyEntryPointBinding) {
        appendComponentForBinding(builder, binding)
        builder.append(".").appendName(
            binding.owner[GeneratorComponent].componentFactoryGenerator.fieldNameFor(binding.dependency)
        ).appendDotAndAccess(binding.getter)
    }

    override fun visitMulti(binding: MultiBinding) {
        binding.owner[GeneratorComponent].collectionBindingGenerator.generateCreation(
            builder = builder,
            binding = binding,
            inside = inside,
            isInsideInnerClass = isInsideInnerClass,
        )
    }

    override fun visitMap(binding: MapBinding) {
        binding.owner[GeneratorComponent].mapBindingGenerator.generateCreation(
            builder = builder,
            binding = binding,
            inside = inside,
            isInsideInnerClass = isInsideInnerClass,
        )
    }

    override fun visitEmpty(binding: EmptyBinding) {
        throw AssertionError("Not reached: unreported empty/missing binding: `$binding`")
    }

    private fun appendComponentForBinding(
        builder: ExpressionBuilder,
        binding: Binding,
   ) {
        appendComponentForBinding(
            builder = builder,
            inside = inside,
            binding = binding,
            isInsideInnerClass = isInsideInnerClass,
        )
    }
}

internal fun Binding.generateCreation(
    builder: ExpressionBuilder,
    inside: BindingGraph,
    isInsideInnerClass: Boolean,
) {
    accept(CreationGeneratorVisitor(
        builder = builder,
        inside = inside,
        isInsideInnerClass = isInsideInnerClass,
    ))
}