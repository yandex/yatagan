package com.yandex.yatagan.core.model

import com.yandex.yatagan.validation.MayBeInvalid

/**
 * Represents [com.yandex.yatagan.Module].
 */
interface ModuleModel : ClassBackedModel, MayBeInvalid {
    /**
     * Included modules.
     */
    val includes: Collection<ModuleModel>

    /**
     * Subcomponents installed by this module.
     */
    val subcomponents: Collection<ComponentModel>

    /**
     * A sequence of all multibinding declarations from the module.
     */
    val multiBindingDeclarations: Sequence<MultiBindingDeclarationModel>

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