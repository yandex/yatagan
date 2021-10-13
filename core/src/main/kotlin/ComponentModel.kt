package com.yandex.dagger3.core

/**
 * Represents @[dagger.Component] annotated class - Component.
 */
interface ComponentModel : ClassBackedModel {
    /**
     * A set of all modules that are included into the component.
     */
    val modules: Set<ModuleModel>

    /**
     * A scope for bindings, that component can cache.
     */
    val scope: Binding.Scope?

    /**
     * A set of component *dependencies*.
     */
    val dependencies: Set<ComponentModel>

    /**
     * A set of [EntryPoint]s in the component.
     */
    val entryPoints: Set<EntryPoint>

    /**
     * An optional explicit factory for this component creation.
     */
    val factory: ComponentFactoryModel?

    /**
     * Whether this component is marked as a component hierarchy root.
     * Do not confuse with [ComponentModel.dependencies] - these are different types of component relations.
     */
    val isRoot: Boolean

    /**
     * Represents a function/property exposed from a component interface.
     * All graph building starts from a set of [EntryPoint]s recursively resolving dependencies.
     */
    data class EntryPoint(
        val getter: MemberCallableNameModel,
        val dep: NodeModel.Dependency,
    ) {
        override fun toString() = "$getter -> $dep"
    }
}