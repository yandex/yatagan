package com.yandex.dagger3.core

/**
 * Represents [dagger.Module].
 */
interface ModuleModel : ClassBackedModel {
    /**
     * Bindings exposed by this module.
     */
    val bindings: Collection<Binding>

    /**
     * Subcomponents installed by this module.
     */
    val subcomponents: Collection<ComponentModel>
}