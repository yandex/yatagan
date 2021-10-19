package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.ComponentModel

internal interface Generator {
    val implName: ClassName
    val factoryGenerator: ComponentFactoryGenerator
    val generator: ProvisionGenerator

    fun forComponent(component: ComponentModel): Generator
    fun forComponent(graph: BindingGraph): Generator = forComponent(graph.component)
}