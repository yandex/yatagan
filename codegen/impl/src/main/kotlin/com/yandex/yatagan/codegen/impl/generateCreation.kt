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
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AlternativesBinding
import com.yandex.yatagan.core.graph.bindings.AssistedInjectFactoryBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyBinding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyEntryPointBinding
import com.yandex.yatagan.core.graph.bindings.ComponentInstanceBinding
import com.yandex.yatagan.core.graph.bindings.ConditionExpressionValueBinding
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
import com.yandex.yatagan.lang.TypeDeclarationKind

private class CreationGeneratorVisitor(
    private val builder: ExpressionBuilder,
    private val inside: BindingGraph,
    private val isInsideInnerClass: Boolean,
) : Binding.Visitor<Unit> {
    override fun visitOther(binding: Binding) = throw AssertionError()

    override fun visitProvision(binding: ProvisionBinding) {
        with(builder) {
            val instance = if (binding.requiresModuleInstance) {
                "%L.%N".formatCode(
                    componentForBinding(binding),
                    binding.owner[GeneratorComponent].componentFactoryGenerator.fieldNameFor(binding.originModule!!),
                )
            } else null
            binding.provision.accept(object : Callable.Visitor<Unit> {
                fun genArgs() {
                    join(seq = binding.inputs.asIterable()) { (node, kind) ->
                        inside.resolveBinding(node).generateAccess(
                            builder = this,
                            inside = inside,
                            kind = kind,
                            isInsideInnerClass = isInsideInnerClass,
                        )
                    }
                }

                override fun visitMethod(method: Method) {
                    val enableNullChecks = inside[GeneratorComponent].options.enableProvisionNullChecks
                    if (enableNullChecks) {
                        +"%T.checkProvisionNotNull(".formatCode(Names.Checks)
                    }
                    if (instance != null) {
                        +"%L.%N(".formatCode(instance, method.name)
                    } else {
                        val ownerObject = when (method.owner.kind) {
                            TypeDeclarationKind.KotlinObject -> ".INSTANCE"
                            else -> ""
                        }
                        +"%T%L.%L(".formatCode(method.ownerName.asTypeName(), ownerObject, method.name)
                    }
                    genArgs()
                    +")"
                    if (enableNullChecks) {
                        +")"
                    }
                }

                override fun visitConstructor(constructor: Constructor) {
                    +"new %T(".formatCode(constructor.constructee.asType().typeName().asRawType())
                    genArgs()
                    +")"
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
        with(builder) {
            +"%L.%N".formatCode(
                componentForBinding(binding),
                binding.owner[GeneratorComponent].componentFactoryGenerator.fieldNameFor(binding.target),
            )
        }
    }

    override fun visitAlternatives(binding: AlternativesBinding) {
        data class AlternativesGenerationEntry(
            val access: CodeBlock,
            val condition: CodeBlock?,
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
                        access = buildExpression {
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
                        access = buildExpression {
                            altBinding.generateAccess(
                                builder = this,
                                inside = inside,
                                isInsideInnerClass = isInsideInnerClass,
                            )
                        },
                        condition = buildExpression {
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

        with(builder) {
            for ((access, condition) in entriesToGenerate) {
                +(condition ?: break)
                +" ? "
                +access
                +" : "
            }
            +entriesToGenerate.last().access
        }
    }

    override fun visitSubComponent(binding: SubComponentBinding) {
        with(builder) {
            +"new %T(".formatCode(binding.targetGraph[GeneratorComponent].componentFactoryGenerator.implName)
            join(binding.targetGraph.usedParents) { parentGraph ->
                +buildExpression {
                    +"%L".formatCode(componentInstance(
                        inside = inside,
                        graph = parentGraph,
                        isInsideInnerClass = isInsideInnerClass,
                    ))
                }
            }
            +")"
        }
    }

    override fun visitComponentDependency(binding: ComponentDependencyBinding) {
        with(builder) {
            +"%L.%N".formatCode(
                componentForBinding(binding),
                binding.owner[GeneratorComponent].componentFactoryGenerator.fieldNameFor(binding.dependency),
            )
        }
    }

    override fun visitComponentInstance(binding: ComponentInstanceBinding) {
        with(builder) {
            +componentForBinding(binding)
        }
    }

    override fun visitComponentDependencyEntryPoint(binding: ComponentDependencyEntryPointBinding) {
        with(builder) {
            +"%L.%N.%N()".formatCode(
                componentForBinding(binding),
                binding.owner[GeneratorComponent].componentFactoryGenerator.fieldNameFor(binding.dependency),
                binding.getter.name,
            )
        }
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

    override fun visitConditionExpressionValue(binding: ConditionExpressionValueBinding) {
        val expression = binding.model.expression ?: run {
            with(builder) { +"false" }
            return
        }

        binding.owner[GeneratorComponent].conditionGenerator.expression(
            builder = builder,
            conditionScope = expression,
            inside = inside,
            isInsideInnerClass = isInsideInnerClass,
        )
    }

    override fun visitEmpty(binding: EmptyBinding) {
        throw AssertionError("Not reached: unreported empty/missing binding: `${binding.target.toString(null)}`")
    }

    private fun componentForBinding(binding: Binding): CodeBlock {
        return componentForBinding(
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