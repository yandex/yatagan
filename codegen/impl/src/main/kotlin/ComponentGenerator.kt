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
import com.squareup.javapoet.TypeSpec
import com.yandex.yatagan.Yatagan
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.codegen.poetry.buildClass
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.component1
import com.yandex.yatagan.core.graph.component2
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method
import javax.inject.Inject
import javax.inject.Singleton
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

@Singleton
internal class ComponentGenerator @Inject constructor(
    component: GeneratorComponent,
    options: Options,
    private val graph: BindingGraph,
    private val contributors: List<Contributor>,
    private val generatedClassName: ClassName,
) {
    interface Contributor {
        fun generate(builder: TypeSpecBuilder)
    }

    data class Options(
        val maxSlotsPerSwitch: Int,
    )

    init {
        graph[GeneratorComponent] = component
    }

    private val childGenerators: Collection<ComponentGenerator> = graph.children.map { childGraph ->
        Yatagan.builder(GeneratorComponent.Factory::class.java).create(
            graph = childGraph,
            options = options,
        ).generator
    }

    fun generate(): TypeSpec = buildClass(generatedClassName) {
        val componentInterface = graph.model.typeName()

        annotation<SuppressWarnings> { stringValues("unchecked", "rawtypes", "NullableProblems") }
        modifiers(FINAL)
        if (!graph.isRoot) {
            modifiers(/*package-private*/ STATIC)
        } else {
            modifiers(PUBLIC)
        }
        implements(componentInterface)

        graph.entryPoints.forEach { (getter, dependency) ->
            // TODO: reuse entry-points as accessors to reduce method count.
            overrideMethod(getter) {
                modifiers(PUBLIC)
                +buildExpression {
                    +"return "
                    graph.resolveBinding(dependency.node).generateAccess(
                        builder = this,
                        inside = graph,
                        kind = dependency.kind,
                        isInsideInnerClass = false,
                    )
                }
            }
        }
        graph.memberInjectors.forEach { membersInjector ->
            overrideMethod(membersInjector.injector) {
                val instanceName = membersInjector.injector.parameters.first().name
                modifiers(PUBLIC)
                membersInjector.membersToInject.forEach { (member, dependency) ->
                    val binding = graph.resolveBinding(dependency.node)
                    +buildExpression {
                        member.accept(object : Member.Visitor<Unit> {
                            override fun visitMethod(model: Method) {
                                +"%N.%N(".formatCode(instanceName, member.name)
                                binding.generateAccess(
                                    builder = this@buildExpression,
                                    inside = graph,
                                    kind = dependency.kind,
                                    isInsideInnerClass = false,
                                )
                                +")"
                            }

                            override fun visitField(model: Field) {
                                +"%N.%N = ".formatCode(instanceName, member.name)
                                binding.generateAccess(
                                    builder = this@buildExpression,
                                    inside = graph,
                                    kind = dependency.kind,
                                    isInsideInnerClass = false,
                                )
                            }
                        })
                    }
                }
            }
        }

        graph.subComponentFactoryMethods.forEach { factory ->
            val createdGraph = factory.createdGraph ?: return@forEach
            overrideMethod(factory.model.factoryMethod) {
                modifiers(PUBLIC)
                +buildExpression {
                    +"return new %T(".formatCode(createdGraph[GeneratorComponent].implementationClassName)
                    val arguments = buildList {
                        for (parentGraph in createdGraph.usedParents) {
                            add(componentInstance(
                                inside = graph,
                                graph = parentGraph,
                                isInsideInnerClass = false,
                            ))
                        }
                        for (input in factory.model.factoryInputs) {
                            add(buildExpression { +input.name })
                        }
                    }
                    join(arguments) { +it }
                    +")"
                }
            }
        }

        childGenerators.forEach { childGenerator ->
            nestedType {
                childGenerator.generate()
            }
        }

        contributors.forEach {
            it.generate(this)
        }
    }
}
