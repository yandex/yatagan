package com.yandex.dagger3.generator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeSpec
import com.yandex.dagger3.core.BindingGraph
import com.yandex.dagger3.generator.poetry.TypeSpecBuilder
import com.yandex.dagger3.generator.poetry.buildClass
import com.yandex.dagger3.generator.poetry.buildExpression
import javax.inject.Provider
import javax.lang.model.element.Modifier
import javax.lang.model.element.Modifier.FINAL

internal class ComponentGenerator(
    private val graph: BindingGraph,
    val targetClassName: ClassName = graph.component.name.asClassName { "Dagger$it" },
    private val parentGenerators: Map<BindingGraph, ComponentGenerator> = emptyMap(),
) {
    interface Contributor {
        fun generate(builder: TypeSpecBuilder)
    }

    private val contributors = mutableListOf<Contributor>()
    private val methodsNs = Namespace()
    private val fieldsNs = Namespace(prefix = "m")

    private val componentFactoryGenerator = LazyProvider {
        ComponentFactoryGenerator(
            graph = graph,
            fieldsNs = fieldsNs,
            componentImplName = targetClassName,
        ).also(this::registerContributor)
    }
    private val slotSwitchingGenerator: Provider<SlotSwitchingGenerator> = LazyProvider {
        SlotSwitchingGenerator(
            methodsNs = methodsNs,
            provisionGenerator = provisionGenerator,
        ).also(this::registerContributor)
    }
    private val unscopedProviderGenerator = LazyProvider {
        UnscopedProviderGenerator(
            componentImplName = targetClassName,
        ).also(this::registerContributor)
    }
    private val scopedProviderGenerator = LazyProvider {
        ScopedProviderGenerator(
            componentImplName = targetClassName,
        ).also(this::registerContributor)
    }
    private val provisionGenerator = LazyProvider {
        ProvisionGenerator(
            graph = graph,
            fieldsNs = fieldsNs,
            methodsNs = methodsNs,
            multiFactory = slotSwitchingGenerator,
            unscopedProviderGenerator = unscopedProviderGenerator,
            scopedProviderGenerator = scopedProviderGenerator,
            componentFactoryGenerator = componentFactoryGenerator,
        ).also(this::registerContributor)
    }

    fun generate(): TypeSpec = buildClass(targetClassName) {
        val componentInterface = graph.component.name.asTypeName()

        annotation<SuppressWarnings> { stringValues("unchecked", "rawtypes", "NullableProblems") }
        modifiers(FINAL)
        implements(componentInterface)

        graph.component.entryPoints.forEach { (getter, dependency) ->
            // TODO: reuse entry-points as accessors to reduce method count.
            method(getter.functionName()) {
                modifiers(Modifier.PUBLIC)
                annotation<Override>()
                returnType(dependency.asTypeName())
                +buildExpression {
                    +"return "
                    provisionGenerator.get().generateAccess(this, dependency)
                }
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
}

