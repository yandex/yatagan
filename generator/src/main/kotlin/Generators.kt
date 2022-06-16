package com.yandex.daggerlite.generator

import com.yandex.daggerlite.graph.BindingGraph

internal class GeneratorsHolder {
    private val data: MutableMap<BindingGraph, GeneratorContainer> = hashMapOf()
    private var frozen = false

    operator fun get(graph: BindingGraph): GeneratorContainer {
        assert(frozen) { "Access to incomplete generator holder" }
        return checkNotNull(data[graph]) {
            "Unknown graph"
        }
    }
    operator fun set(graph: BindingGraph, generatorContainer: GeneratorContainer) {
        assert(!frozen) { "Can't modify generator holder after it's frozen" }
        data[graph] = generatorContainer
    }

    fun freeze() {
        frozen = true
    }
}

// FIXME: Do not use static state here, refactor it and pass where required.
internal val GeneratorsBackingTL = ThreadLocal<GeneratorsHolder>()

internal val Generators get() = GeneratorsBackingTL.get()