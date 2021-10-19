package com.yandex.daggerlite.generator

import com.squareup.javapoet.JavaFile
import com.yandex.daggerlite.core.BindingGraph

class ComponentGeneratorFacade(
    graph: BindingGraph,
) {
    private val generator = ComponentGenerator(
        graph = graph,
    )

    val targetPackageName: String
        get() = generator.implName.packageName()
    val targetClassName: String
        get() = generator.implName.simpleName()

    val targetLanguage: Language get() = Language.Java

    fun generateTo(out: Appendable) {
        JavaFile.builder(targetPackageName, generator.generate())
            .build()
            .writeTo(out)
    }
}
