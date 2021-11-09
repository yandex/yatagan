package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName

internal interface GeneratorContainer {
    val implName: ClassName
    val factoryGenerator: ComponentFactoryGenerator
    val accessStrategyManager: AccessStrategyManager
    val conditionGenerator: ConditionGenerator
    val bootstrapListGenerator: BootstrapListGenerator
}
