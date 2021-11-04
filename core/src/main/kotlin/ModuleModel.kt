package com.yandex.daggerlite.core

/**
 * Represents [com.yandex.daggerlite.Module].
 */
abstract class ModuleModel : ClassBackedModel() {
    /**
     * Included modules.
     * TODO: actually use them.
     */
    abstract val includes: Collection<ModuleModel>

    /**
     * Subcomponents installed by this module.
     */
    abstract val subcomponents: Collection<ComponentModel>

    /**
     * Bindings exposed by this module.
     */
    abstract fun bindings(forGraph: BindingGraph): Sequence<BaseBinding>
}