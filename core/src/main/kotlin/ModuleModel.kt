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
     * Bindings exposed by this module.
     */
    fun bindings(forGraph: BindingGraph): Sequence<BaseBinding>
}