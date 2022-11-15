package com.yandex.yatagan.core.model

import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Represents @[com.yandex.yatagan.Component] annotated class - Component.
 */
public interface ComponentModel : ConditionalHoldingModel, MayBeInvalid, HasNodeModel {
    /**
     * A set of *all* modules that are included into the component (transitively).
     */
    public val modules: Set<ModuleModel>

    /**
     * All supported scopes for bindings, that component can cache.
     */
    public val scopes: Set<Annotation>

    /**
     * A set of component *dependencies*.
     */
    public val dependencies: Set<ComponentDependencyModel>

    /**
     * A set of [EntryPoint]s in the component.
     */
    public val entryPoints: Set<EntryPoint>

    /**
     * A set of [MembersInjectorModel]s defined for this component.
     */
    public val memberInjectors: Set<MembersInjectorModel>

    /**
     * An optional explicit factory for this component creation.
     */
    public val factory: ComponentFactoryModel?

    /**
     * Whether this component is marked as a component hierarchy root.
     * Do not confuse with [ComponentModel.dependencies] - these are different types of component relations.
     */
    public val isRoot: Boolean

    /**
     * TODO: doc.
     */
    public val variant: Variant

    /**
     * `true` if this component entry-points and/or their dependencies can be accessed from multiple threads.
     * `false` otherwise.
     */
    public val requiresSynchronizedAccess: Boolean

    /**
     * Represents a function/property exposed from a component interface.
     * All graph building starts from a set of [EntryPoint]s recursively resolving dependencies.
     */
    public interface EntryPoint : MayBeInvalid {
        public val getter: Method
        public val dependency: NodeDependency
    }
}