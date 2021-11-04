package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.lang.AnnotationLangModel

internal interface Generator {
    val implName: ClassName
    val factoryGenerator: ComponentFactoryGenerator
    val generator: ProvisionGenerator
    val conditionGenerator: ConditionGenerator
}

internal interface Generators {
    operator fun get(graph: BindingGraph): Generator
    fun forScope(scope: AnnotationLangModel): Generator
}

internal class GeneratorsBuilder : Generators {
    private val data: MutableMap<BindingGraph, Generator> = hashMapOf()

    override fun get(graph: BindingGraph): Generator {
        return checkNotNull(data[graph]) {
            "Unknown graph"
        }
    }

    override fun forScope(scope: AnnotationLangModel): Generator {
        return data.entries.single { (graph, _) -> graph.model.scope == scope }.value
    }

    operator fun set(graph: BindingGraph, generator: Generator) {
        data[graph] = generator
    }
}