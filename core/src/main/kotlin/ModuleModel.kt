package com.yandex.daggerlite.core

/**
 * Represents [com.yandex.daggerlite.Module].
 */
interface ModuleModel : ClassBackedModel {
    /**
     * Bindings exposed by this module.
     */
    val bindings: Collection<BaseBinding>

    /**
     * Subcomponents installed by this module.
     */
    val subcomponents: Collection<ComponentModel>

    /**
     * TODO: doc
     */
    val isInstanceRequired: Boolean
}