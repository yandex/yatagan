package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel

/**
 * Represents @[com.yandex.daggerlite.Component] annotated class - Component.
 */
interface ComponentModel : ClassBackedModel {
    /**
     * A set of all modules that are included into the component.
     */
    val modules: Set<ModuleModel>

    /**
     * A scope for bindings, that component can cache.
     */
    val scope: AnnotationLangModel?

    /**
     * A set of component *dependencies*.
     */
    val dependencies: Set<ComponentModel>

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
     * TODO: doc.
     */
    fun conditionScope(forVariant: Variant): ConditionScope?

    /**
     * TODO: doc.
     */
    fun asNode(): NodeModel

    /**
     * Represents a function/property exposed from a component interface.
     * All graph building starts from a set of [EntryPoint]s recursively resolving dependencies.
     */
    data class EntryPoint(
        val getter: FunctionLangModel,
        val dependency: NodeDependency,
    )
}