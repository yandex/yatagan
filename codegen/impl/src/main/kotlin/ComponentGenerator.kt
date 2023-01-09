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
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.codegen.poetry.buildClass
import com.yandex.yatagan.codegen.poetry.buildExpression
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.component1
import com.yandex.yatagan.core.graph.component2
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.compiled.ClassNameModel
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

internal class ComponentGenerator private constructor(
    private val graph: BindingGraph,
    val generatedClassName: ClassName,
    maxSlotsPerSwitch: Int,
) {
    constructor(
        graph: BindingGraph,
        maxSlotsPerSwitch: Int,
    ): this(
        graph = graph,
        maxSlotsPerSwitch = maxSlotsPerSwitch,
        generatedClassName = graph.model.name.let { name ->
            check(name is ClassNameModel)
            // Keep name mangling in sync with loader!
            ClassName.get(name.packageName, "Yatagan$" + name.simpleNames.joinToString(separator = "$"))
        },
    )

    interface Contributor {
        fun generate(builder: TypeSpecBuilder)
    }

    private val contributors = mutableListOf<Contributor>()
    private val methodsNs = Namespace()
    private val fieldsNs = Namespace(prefix = "m")
    private val subcomponentNs = Namespace()

    private val slotSwitchingGenerator = lazyProvider {
        SlotSwitchingGenerator(
            thisGraph = graph,
            maxSlotsPerSwitch = maxSlotsPerSwitch,
        ).also(::registerContributor)
    }
    private val unscopedProviderGenerator = lazyProvider {
        UnscopedProviderGenerator(
            componentImplName = generatedClassName,
        ).also(::registerContributor)
    }
    private val scopedProviderGenerator = lazyProvider {
        ScopedProviderGenerator(
            componentImplName = generatedClassName,
            useDoubleChecking = graph.requiresSynchronizedAccess,
        ).also(::registerContributor)
    }
    private val lockGeneratorProvider = lazyProvider {
        LockGenerator(
            componentImplName = generatedClassName,
        ).also(::registerContributor)
    }

    init {
        graph[ComponentImplClassName] = generatedClassName

        graph[ConditionGenerator] = ConditionGenerator(
            fieldsNs = fieldsNs,
            methodsNs = methodsNs,
            thisGraph = graph,
        ).also(::registerContributor)

        graph[CollectionBindingGenerator] = CollectionBindingGenerator(
            methodsNs = methodsNs,
            thisGraph = graph,
        ).also(::registerContributor)

        graph[MapBindingGenerator] = MapBindingGenerator(
            methodsNs = methodsNs,
            thisGraph = graph,
        ).also(::registerContributor)

        graph[AssistedInjectFactoryGenerator] = AssistedInjectFactoryGenerator(
            thisGraph = graph,
            componentImplName = generatedClassName,
        ).also(::registerContributor)

        graph[AccessStrategyManager] = AccessStrategyManager(
            thisGraph = graph,
            fieldsNs = fieldsNs,
            methodsNs = methodsNs,
            multiFactory = slotSwitchingGenerator,
            unscopedProviderGenerator = unscopedProviderGenerator,
            scopedProviderGenerator = scopedProviderGenerator,
            lockGenerator = lockGeneratorProvider,
        ).also(::registerContributor)

        graph[ComponentFactoryGenerator] = ComponentFactoryGenerator(
            thisGraph = graph,
            fieldsNs = fieldsNs,
            componentImplName = generatedClassName,
        ).also(::registerContributor)
    }

    private val childGenerators: Collection<ComponentGenerator> = graph.children.map { childGraph ->
        ComponentGenerator(
            graph = childGraph,
            maxSlotsPerSwitch = maxSlotsPerSwitch,
            generatedClassName = generatedClassName.nestedClass(
                subcomponentNs.name(childGraph.model.name, suffix = "Impl", firstCapital = true)
            ),
        )
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

        childGenerators.forEach { childGenerator ->
            nestedType {
                childGenerator.generate()
            }
        }

        contributors.forEach {
            it.generate(this)
        }
    }

    private fun registerContributor(contributor: Contributor) {
        contributors += contributor
    }

    private class LazyProvider<T : Any>(initializer: () -> T) : Provider<T> {
        private val instance = lazy(initializer)
        override fun get(): T = instance.value
    }

    private fun <T : Any> lazyProvider(initializer: () -> T): Provider<T> = LazyProvider(initializer)
}

