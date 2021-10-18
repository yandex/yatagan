package com.yandex.dagger3.generator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeSpec
import com.yandex.dagger3.core.BindingGraph
import com.yandex.dagger3.core.ComponentModel
import com.yandex.dagger3.generator.poetry.TypeSpecBuilder
import com.yandex.dagger3.generator.poetry.buildClass
import com.yandex.dagger3.generator.poetry.buildExpression
import javax.inject.Provider
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC

internal class ComponentGenerator(
    private val graph: BindingGraph,
    val targetClassName: ClassName = graph.component.name.asClassName { "Dagger$it" },
    private val parentGenerators: Map<ComponentModel, ComponentGenerator> = emptyMap(),
) {
    interface Contributor {
        fun generate(builder: TypeSpecBuilder)
    }

    private val contributors = mutableListOf<Contributor>()
    private val methodsNs = Namespace()
    private val fieldsNs = Namespace(prefix = "m")
    private val subcomponentNs = Namespace()

    private val childGenerators: Map<ComponentModel, ComponentGenerator> = graph.children.associateBy(
        keySelector = { it.component }
    ) {
        ComponentGenerator(
            graph = it,
            targetClassName = targetClassName.nestedClass(subcomponentNs.name(it.component.name, "Impl")),
            parentGenerators = parentGenerators + mapOf(graph.component to this)
        )
    }
    private val generators = childGenerators + parentGenerators
    val componentFactoryGenerator = lazyProvider {
        ComponentFactoryGenerator(
            graph = graph,
            fieldsNs = fieldsNs,
            componentImplName = targetClassName,
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
            componentImplName = targetClassName,
        ).also(this::registerContributor)
    }
    private val scopedProviderGenerator = lazyProvider {
        ScopedProviderGenerator(
            componentImplName = targetClassName,
        ).also(this::registerContributor)
    }
    val provisionGenerator = lazyProvider {
        ProvisionGenerator(
            graph = graph,
            fieldsNs = fieldsNs,
            methodsNs = methodsNs,
            multiFactory = slotSwitchingGenerator,
            unscopedProviderGenerator = unscopedProviderGenerator,
            scopedProviderGenerator = scopedProviderGenerator,
            componentFactoryGenerator = componentFactoryGenerator,
            generators = generators,
        ).also(this::registerContributor)
    }

    fun generate(): TypeSpec = buildClass(targetClassName) {
        val componentInterface = graph.component.name.asTypeName()

        annotation<SuppressWarnings> { stringValues("unchecked", "rawtypes", "NullableProblems") }
        modifiers(FINAL)
        if (!graph.component.isRoot) {
            modifiers(PRIVATE, STATIC)
        }
        implements(componentInterface)

        graph.component.entryPoints.forEach { (getter, dependency) ->
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

