package com.yandex.daggerlite.core

/**
 * Represents [com.yandex.daggerlite.Module].
 */
interface ModuleModel : ClassBackedModel {
    /**
     * Included modules.
     */
    val includes: Collection<ModuleModel>

    /**
     * Subcomponents installed by this module.
     */
    val subcomponents: Collection<ComponentModel>

    /**
     * TODO: doc.
     */
    val listDeclarations: Sequence<ListDeclarationModel>

    /**
     * Whether module instance is required to use some (or all) of its bindings.
     * NOTE: Kotlin's objects and companions does not count as they are handled on lang level.
     */
    val requiresInstance: Boolean

    /**
     * Whether this module can be trivially constructed inside a component.
     * Makes sense when [requiresInstance] is `true`.
     */
    val isTriviallyConstructable: Boolean

    /**
     * [Binding models][ModuleHostedBindingModel] that are declared in this module.
     */
    val bindings: Sequence<ModuleHostedBindingModel>
}