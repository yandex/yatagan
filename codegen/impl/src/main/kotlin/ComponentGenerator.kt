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

import com.yandex.yatagan.Yatagan
import com.yandex.yatagan.codegen.poetry.Access
import com.yandex.yatagan.codegen.poetry.ClassName
import com.yandex.yatagan.codegen.poetry.ExpressionBuilder
import com.yandex.yatagan.codegen.poetry.Poetry
import com.yandex.yatagan.codegen.poetry.TypeName
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.component1
import com.yandex.yatagan.core.graph.component2
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ComponentGenerator @Inject constructor(
    component: GeneratorComponent,
    options: Options,
    private val poetry: Poetry,
    private val graph: BindingGraph,
    private val contributors: List<Contributor>,
    private val generatedClassName: ClassName,
) {
    interface Contributor {
        fun generate(builder: TypeSpecBuilder)
    }

    data class Options(
        val maxSlotsPerSwitch: Int,
        val enableProvisionNullChecks: Boolean,
        val enableThreadChecks: Boolean,
    )

    init {
        graph[GeneratorComponent] = component
    }

    private val childGenerators: Collection<ComponentGenerator> = graph.children.map { childGraph ->
        Yatagan.builder(GeneratorComponent.Factory::class.java).create(
            poetry = poetry,
            graph = childGraph,
            options = options,
        ).generator
    }

    fun generateRoot(into: Appendable) {
        check(graph.isRoot)
        poetry.buildClass(generatedClassName, into = into) {
            generate()
        }
    }

    fun TypeSpecBuilder.generate() {
        val componentInterface = TypeName.Inferred(graph.model.type)

        addSuppressWarningsAnnotation("unchecked", "rawtypes", "NullableProblems")
        implements(componentInterface)

        graph.entryPoints.forEach { (getter, dependency) ->
            // TODO: reuse entry-points as accessors to reduce method count.
            overrideMethod(getter) {
                code {
                    appendReturnStatement {
                        graph.resolveBinding(dependency.node).generateAccess(
                            builder = this,
                            inside = graph,
                            kind = dependency.kind,
                            isInsideInnerClass = false,
                        )
                    }
                }
            }
        }
        graph.memberInjectors.forEach { membersInjector ->
            overrideMethod(membersInjector.injector) {
                val instanceName = membersInjector.injector.parameters.first().name
                membersInjector.membersToInject.forEach { (member, dependency) ->
                    val binding = graph.resolveBinding(dependency.node)
                    code {
                        member.accept(object : Member.Visitor<Unit> {
                            override fun visitOther(model: Member) = throw AssertionError()

                            override fun visitMethod(model: Method) {
                                appendAssignment(
                                    receiver = { appendName(instanceName) },
                                    setter = model,
                                    value = {
                                        binding.generateAccess(
                                            builder = this,
                                            inside = graph,
                                            kind = dependency.kind,
                                            isInsideInnerClass = false,
                                        )
                                    },
                                )
                            }

                            override fun visitField(model: Field) {
                                appendStatement {
                                    appendName(instanceName).append(".")
                                        .appendName(model.name).append(" = ")
                                    binding.generateAccess(
                                        builder = this,
                                        inside = graph,
                                        kind = dependency.kind,
                                        isInsideInnerClass = false,
                                    )
                                }
                            }
                        })
                    }
                }
            }
        }

        graph.subComponentFactoryMethods.forEach { factory ->
            val createdGraph = factory.createdGraph ?: return@forEach
            overrideMethod(factory.model.factoryMethod) {
                code {
                    val arguments = buildList<ExpressionBuilder.() -> Unit> {
                        for (parentGraph in createdGraph.usedParents) {
                            add {
                                appendComponentInstance(
                                    builder = this,
                                    inside = graph,
                                    graph = parentGraph,
                                    isInsideInnerClass = false,
                                )
                            }
                        }
                        for (input in factory.model.factoryInputs) {
                            add {
                                appendName(input.name)
                            }
                        }
                    }
                    appendReturnStatement {
                        appendObjectCreation(
                            type = createdGraph[GeneratorComponent].implementationClassName,
                            argumentCount = arguments.size,
                            argument = { arguments[it]() }
                        )
                    }
                }
            }
        }

        childGenerators.forEach { childGenerator ->
            nestedClass(
                name = childGenerator.generatedClassName,
                access = Access.Internal,
                isInner = false,
            ) {
                with(childGenerator) { generate() }
            }
        }

        contributors.forEach {
            it.generate(this)
        }
    }
}
