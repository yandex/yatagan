package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.core.BindingGraph

internal interface Generator {
    val implName: ClassName
    val factoryGenerator: ComponentFactoryGenerator
    val generator: ProvisionGenerator
}

internal interface Generators {
    operator fun get(graph: BindingGraph): Generator
}

internal class GeneratorsBuilder : Generators {
    private val data: MutableMap<BindingGraph, Generator> = hashMapOf()

    override fun get(graph: BindingGraph): Generator {
        return checkNotNull(data[graph]) {
            "Unknown graph"
        }
    }

    operator fun set(graph: BindingGraph, generator: Generator) {
        data[graph] = generator
    }
}