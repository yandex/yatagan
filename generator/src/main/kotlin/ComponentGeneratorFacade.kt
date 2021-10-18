package com.yandex.dagger3.generator

import com.squareup.javapoet.JavaFile
import com.yandex.dagger3.core.BindingGraph

class ComponentGeneratorFacade(
    graph: BindingGraph,
) {
    private val generator = ComponentGenerator(
        graph = graph,
    )

    val targetPackageName: String
        get() = generator.targetClassName.packageName()
    val targetClassName: String
        get() = generator.targetClassName.simpleName()

    val targetLanguage: Language get() = Language.Java

    fun generateTo(out: Appendable) {
        JavaFile.builder(targetPackageName, generator.generate())
            .build()
            .writeTo(out)
    }
}
