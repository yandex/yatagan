package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.validation.MayBeInvalid

/**
 * Represents @[com.yandex.daggerlite.Component] annotated class - Component.
 */
interface ComponentModel : ConditionalHoldingModel, MayBeInvalid, HasNodeModel {
    /**
     * A set of *all* modules that are included into the component (transitively).
     */
    val modules: Set<ModuleModel>

    /**
     * A scope for bindings, that component can cache.
     */
    val scope: AnnotationLangModel?

    /**
     * A set of component *dependencies*.
     */
    val dependencies: Set<ComponentDependencyModel>

    /**
     * A set of [EntryPoint]s in the component.
     */
    val entryPoints: Set<EntryPoint>

    /**
     * A set of [MembersInjectorModel]s defined for this component.
     */
    val memberInjectors: Set<MembersInjectorModel>

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
     * TODO: doc.
     */
    val variant: Variant

    /**
     * `true` if this component entry-points and/or their dependencies can be accessed from multiple threads.
     * `false` otherwise.
     */
    val requiresSynchronizedAccess: Boolean

    /**
     * Represents a function/property exposed from a component interface.
     * All graph building starts from a set of [EntryPoint]s recursively resolving dependencies.
     */
    interface EntryPoint : MayBeInvalid {
        val getter: FunctionLangModel
        val dependency: NodeDependency
    }
}