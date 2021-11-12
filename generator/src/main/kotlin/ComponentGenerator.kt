package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeSpec
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.BootstrapListBinding
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildClass
import com.yandex.daggerlite.generator.poetry.buildExpression
import javax.inject.Provider
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

internal class ComponentGenerator(
    private val graph: BindingGraph,
    val generatedClassName: ClassName = graph.model.name.asClassName { "Dagger$it" },
) {
    interface Contributor {
        fun generate(builder: TypeSpecBuilder)
    }

    private val contributors = mutableListOf<Contributor>()
    private val methodsNs = Namespace()
    private val fieldsNs = Namespace(prefix = "m")
    private val subcomponentNs = Namespace()

    private val slotSwitchingGenerator: Provider<SlotSwitchingGenerator> = lazyProvider {
        SlotSwitchingGenerator(
            thisGraph = graph,
            methodsNs = methodsNs,
        ).also(this::registerContributor)
    }
    private val unscopedProviderGenerator = lazyProvider {
        UnscopedProviderGenerator(
            componentImplName = generatedClassName,
        ).also(this::registerContributor)
    }
    private val scopedProviderGenerator = lazyProvider {
        ScopedProviderGenerator(
            componentImplName = generatedClassName,
        ).also(this::registerContributor)
    }

    init {
        Generators[graph] = object : GeneratorContainer {
            override val implName: ClassName get() = this@ComponentGenerator.generatedClassName

            override val accessStrategyManager = AccessStrategyManager(
                thisGraph = graph,
                fieldsNs = fieldsNs,
                methodsNs = methodsNs,
                multiFactory = slotSwitchingGenerator,
                unscopedProviderGenerator = unscopedProviderGenerator,
                scopedProviderGenerator = scopedProviderGenerator,
            ).also(this@ComponentGenerator::registerContributor)

            override val conditionGenerator = ConditionGenerator(
                fieldsNs = fieldsNs,
                thisGraph = graph,
            ).also(this@ComponentGenerator::registerContributor)

            override val bootstrapListGenerator = BootstrapListGenerator(
                bootstrapListBindings = graph.localBindings.keys.filterIsInstance<BootstrapListBinding>(),
                methodNs = methodsNs,
                thisGraph = graph,
            ).also(this@ComponentGenerator::registerContributor)

            override val factoryGenerator = ComponentFactoryGenerator(
                thisGraph = graph,
                fieldsNs = fieldsNs,
                componentImplName = implName,
            ).also(this@ComponentGenerator::registerContributor)
        }
    }

    private val childGenerators: Map<ComponentModel, ComponentGenerator> = graph.children.associateBy(
        keySelector = BindingGraph::model
    ) { childGraph ->
        ComponentGenerator(
            graph = childGraph,
            generatedClassName = generatedClassName.nestedClass(subcomponentNs.name(childGraph.model.name, "Impl")),
        )
    }

    fun generate(): TypeSpec = buildClass(generatedClassName) {
        val componentInterface = graph.model.typeName()

        annotation<SuppressWarnings> { stringValues("unchecked", "rawtypes", "NullableProblems") }
        modifiers(FINAL)
        if (!graph.model.isRoot) {
            modifiers(PRIVATE, STATIC)
        } else {
            modifiers(PUBLIC)
        }
        implements(componentInterface)

        graph.model.entryPoints.forEach { (getter, dependency) ->
            // TODO: reuse entry-points as accessors to reduce method count.
            method(getter.name) {
                modifiers(PUBLIC)
                annotation<Override>()
                returnType(getter.returnType.typeName())
                +buildExpression {
                    +"return "
                    graph.resolveBinding(dependency.node)
                        .generateAccess(builder = this, inside = graph, kind = dependency.kind)
                }
            }
        }

        childGenerators.values.forEach { childGenerator ->
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
        private val instance by lazy(initializer)
        override fun get(): T = instance
    }

    private fun <T : Any> lazyProvider(initializer: () -> T): Provider<T> = LazyProvider(initializer)
}

