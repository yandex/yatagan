package com.yandex.yatagan.codegen.impl

import com.squareup.javapoet.ClassName
import com.yandex.yatagan.Binds
import com.yandex.yatagan.BindsInstance
import com.yandex.yatagan.Component
import com.yandex.yatagan.ConditionsApi
import com.yandex.yatagan.IntoList
import com.yandex.yatagan.Module
import com.yandex.yatagan.Provides
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.Extensible
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
internal annotation class FieldsNamespace

@Qualifier
internal annotation class MethodsNamespace

@Qualifier
internal annotation class SubcomponentsNamespace

@Singleton
@Component(modules = [
    GeneratorComponent.GutsModule::class,
    GeneratorComponent.ContributorsModule::class,
])
internal interface GeneratorComponent {
    val generator: ComponentGenerator

    val implementationClassName: ClassName

    val conditionGenerator: ConditionGenerator

    val collectionBindingGenerator: CollectionBindingGenerator

    val mapBindingGenerator: MapBindingGenerator

    val assistedInjectFactoryGenerator: AssistedInjectFactoryGenerator

    val accessStrategyManager: AccessStrategyManager

    val componentFactoryGenerator: ComponentFactoryGenerator

    @get:SubcomponentsNamespace
    val subcomponentsNamespace: Namespace

    @Component.Builder
    interface Factory {
        fun create(
            @BindsInstance graph: BindingGraph,
            @BindsInstance options: ComponentGenerator.Options,
        ): GeneratorComponent
    }

    companion object Key : Extensible.Key<GeneratorComponent> {
        override val keyType get() = GeneratorComponent::class.java
    }

    @Module
    interface ContributorsModule {
        @[Binds IntoList]
        fun contributor1(i: SlotSwitchingGenerator): ComponentGenerator.Contributor
        @[Binds IntoList]
        fun contributor2(i: UnscopedProviderGenerator): ComponentGenerator.Contributor
        @[Binds IntoList]
        fun contributor3(i: ScopedProviderGenerator): ComponentGenerator.Contributor
        @[Binds IntoList]
        fun contributor4(i: LockGenerator): ComponentGenerator.Contributor
        @[Binds IntoList]
        fun contributor5(i: ConditionGenerator): ComponentGenerator.Contributor
        @[Binds IntoList]
        fun contributor6(i: CollectionBindingGenerator): ComponentGenerator.Contributor
        @[Binds IntoList]
        fun contributor7(i: MapBindingGenerator): ComponentGenerator.Contributor
        @[Binds IntoList]
        fun contributor8(i: AssistedInjectFactoryGenerator): ComponentGenerator.Contributor
        @[Binds IntoList]
        fun contributor9(i: AccessStrategyManager): ComponentGenerator.Contributor
        @[Binds IntoList]
        fun contributor10(i: ComponentFactoryGenerator): ComponentGenerator.Contributor
    }

    @Module
    @OptIn(ConditionsApi::class)  // TODO: Remove once the IDE warning is fixed.
    class GutsModule {
        @[Provides Singleton MethodsNamespace]
        fun provideMethodsNamespace(): Namespace = Namespace()

        @[Provides Singleton FieldsNamespace]
        fun provideFieldsNamespace(): Namespace = Namespace(prefix = "m")

        @[Provides Singleton SubcomponentsNamespace]
        fun provideSubcomponentsNamespace(): Namespace = Namespace()

        @[Provides Singleton]
        fun provideImplementationName(graph: BindingGraph): ClassName = formatImplementationClassName(graph)
    }
}
