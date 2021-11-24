package com.yandex.daggerlite.generator

import com.yandex.daggerlite.graph.BindingGraph
import java.io.Closeable

internal object Generators : Closeable {
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

    override fun close() {
        data.clear()
        frozen = false
    }
}