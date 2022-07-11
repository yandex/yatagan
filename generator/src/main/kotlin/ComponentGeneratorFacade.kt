package com.yandex.daggerlite.generator

import com.squareup.javapoet.JavaFile
import com.yandex.daggerlite.graph.BindingGraph

class ComponentGeneratorFacade(
    graph: BindingGraph,
) {
    private val generator = ComponentGenerator(
        graph = graph,
    )

    val targetPackageName: String
        get() = generator.generatedClassName.packageName()

    val targetClassName: String
        get() = generator.generatedClassName.simpleName()

    fun generateTo(out: Appendable) {
        JavaFile.builder(targetPackageName, generator.generate())
            .build()
            .writeTo(out)
    }
}
