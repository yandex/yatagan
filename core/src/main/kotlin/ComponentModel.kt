package com.yandex.daggerlite.core

/**
 * Represents @[com.yandex.daggerlite.Component] annotated class - Component.
 */
abstract class ComponentModel : NodeModel() {
    /**
     * A set of all modules that are included into the component.
     */
    abstract val modules: Set<ModuleModel>

    /**
     * A scope for bindings, that component can cache.
     */
    abstract val scope: ProvisionBinding.Scope?

    /**
     * A set of component *dependencies*.
     */
    abstract val dependencies: Set<ComponentModel>

    /**
     * A set of [EntryPoint]s in the component.
     */
    abstract val entryPoints: Set<EntryPoint>

    /**
     * An optional explicit factory for this component creation.
     */
    abstract val factory: ComponentFactoryModel?

    /**
     * Whether this component is marked as a component hierarchy root.
     * Do not confuse with [ComponentModel.dependencies] - these are different types of component relations.
     */
    abstract val isRoot: Boolean

    /**
     * Represents a function/property exposed from a component interface.
     * All graph building starts from a set of [EntryPoint]s recursively resolving dependencies.
     */
    interface EntryPoint {
        val id: Id

        val dependency: Dependency

        interface Id
    }

    final override val qualifier: Nothing? get() = null

    final override fun implicitBinding(): Nothing? = null
}