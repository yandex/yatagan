package com.yandex.daggerlite.generator

import com.squareup.javapoet.JavaFile
import com.yandex.daggerlite.graph.BindingGraph
import java.io.Closeable

class ComponentGeneratorFacade(
    graph: BindingGraph,
) : Closeable {
    private val generator = ComponentGenerator(
        graph = graph,
    )

    init {
        Generators.freeze()
    }

    val targetPackageName: String
        get() = generator.generatedClassName.packageName()

    val targetClassName: String
        get() = generator.generatedClassName.simpleName()

    fun generateTo(out: Appendable) {
        JavaFile.builder(targetPackageName, generator.generate())
            .build()
            .writeTo(out)
    }

    override fun close() {
        Generators.close()
    }
}
