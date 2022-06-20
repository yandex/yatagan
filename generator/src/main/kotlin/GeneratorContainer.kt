package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.graph.MapBinding
import com.yandex.daggerlite.graph.MultiBinding

internal interface GeneratorContainer {
    val implName: ClassName
    val factoryGenerator: ComponentFactoryGenerator
    val accessStrategyManager: AccessStrategyManager
    val conditionGenerator: ConditionGenerator
    val multiBindingGenerator: MultiBindingGeneratorBase<MultiBinding>
    val mapBindingGenerator: MultiBindingGeneratorBase<MapBinding>
    val assistedInjectFactoryGenerator: AssistedInjectFactoryGenerator
}
