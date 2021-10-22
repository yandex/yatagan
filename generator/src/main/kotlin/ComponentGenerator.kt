package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeSpec
import com.yandex.daggerlite.core.BindingGraph
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
    override val implName: ClassName = graph.model.name.asClassName { "Dagger$it" },
    private val generators: GeneratorsBuilder = GeneratorsBuilder(),
) : Generator {
    interface Contributor {
        fun generate(builder: TypeSpecBuilder)
    }

    private val contributors = mutableListOf<Contributor>()
    private val methodsNs = Namespace()
    private val fieldsNs = Namespace(prefix = "m")
    private val subcomponentNs = Namespace()

    private val childGenerators: Map<ComponentModel, ComponentGenerator> = graph.children.associateBy(
        keySelector = BindingGraph::model
    ) { childGraph ->
        ComponentGenerator(
            graph = childGraph,
            implName = implName.nestedClass(subcomponentNs.name(childGraph.model.name, "Impl")),
            generators = generators,
        )
    }
    private val componentFactoryGenerator = lazyProvider {
        ComponentFactoryGenerator(
            thisGraph = graph,
            fieldsNs = fieldsNs,
            componentImplName = implName,
            generators = generators,
        ).also(this::registerContributor)
    }
    private val slotSwitchingGenerator: Provider<SlotSwitchingGenerator> = lazyProvider {
        SlotSwitchingGenerator(
            methodsNs = methodsNs,
            provisionGenerator = provisionGenerator,
        ).also(this::registerContributor)
    }
    private val unscopedProviderGenerator = lazyProvider {
        UnscopedProviderGenerator(
            componentImplName = implName,
        ).also(this::registerContributor)
    }
    private val scopedProviderGenerator = lazyProvider {
        ScopedProviderGenerator(
            componentImplName = implName,
        ).also(this::registerContributor)
    }
    private val provisionGenerator = lazyProvider {
        ProvisionGenerator(
            thisGraph = graph,
            fieldsNs = fieldsNs,
            methodsNs = methodsNs,
            multiFactory = slotSwitchingGenerator,
            unscopedProviderGenerator = unscopedProviderGenerator,
            scopedProviderGenerator = scopedProviderGenerator,
            generators = generators,
        ).also(this::registerContributor)
    }

    init {
        generators[graph] = this
    }

    fun generate(): TypeSpec = buildClass(implName) {
        val componentInterface = graph.model.typeName()

        annotation<SuppressWarnings> { stringValues("unchecked", "rawtypes", "NullableProblems") }
        modifiers(FINAL)
        if (!graph.model.isRoot) {
            modifiers(PRIVATE, STATIC)
        }
        implements(componentInterface)

        graph.model.entryPoints.forEach { (getter, dependency) ->
            // TODO: reuse entry-points as accessors to reduce method count.
            method(getter.functionName()) {
                modifiers(PUBLIC)
                annotation<Override>()
                returnType(dependency.asTypeName())
                +buildExpression {
                    +"return "
                    provisionGenerator.get().generateAccess(this, dependency)
                }
            }
        }

        childGenerators.values.forEach { childGenerator ->
            nestedType {
                childGenerator.generate()
            }
        }

        // Explicitly instantiate factory generator
        componentFactoryGenerator.get()

        contributors.forEach {
            it.generate(this)
        }
    }

    override val factoryGenerator: ComponentFactoryGenerator
        get() = componentFactoryGenerator.get()

    override val generator: ProvisionGenerator
        get() = provisionGenerator.get()

    private fun registerContributor(contributor: Contributor) {
        contributors += contributor
    }

    private class LazyProvider<T : Any>(initializer: () -> T) : Provider<T> {
        private val instance by lazy(initializer)
        override fun get(): T = instance
    }

    private fun <T : Any> lazyProvider(initializer: () -> T): Provider<T> = LazyProvider(initializer)
}

