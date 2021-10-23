package com.yandex.daggerlite.core

/**
 * Represents [com.yandex.daggerlite.Module].
 */
abstract class ModuleModel : ClassBackedModel() {
    /**
     * Bindings exposed by this module.
     */
    abstract val bindings: Collection<BaseBinding>

    /**
     * Subcomponents installed by this module.
     */
    abstract val subcomponents: Collection<ComponentModel>

    /**
     * TODO: doc
     */
    abstract val isInstanceRequired: Boolean
}