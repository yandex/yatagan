package com.yandex.daggerlite.core

/**
 * Provided dependency component instance.
 *
 * @see ComponentModel
 */
interface ComponentDependencyInput : ComponentFactoryModel.Input {
    val component: ComponentModel
}

/**
 * Provided instance input.
 *
 * @see com.yandex.daggerlite.BindsInstance
 */
interface InstanceInput : ComponentFactoryModel.Input {
    val node: NodeModel
}

/**
 * Provided module instance if it requires externally provided instance.
 *
 * @see ModuleModel
 */
interface ModuleInstanceInput : ComponentFactoryModel.Input {
    val module: ModuleModel
}
