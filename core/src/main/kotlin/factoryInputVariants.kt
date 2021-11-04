package com.yandex.daggerlite.core

interface ComponentDependencyInput : ComponentFactoryModel.Input {
    fun createBinding(forGraph: BindingGraph): ComponentDependencyBinding
    val component: ComponentModel
}

interface InstanceInput : ComponentFactoryModel.Input {
    fun createBinding(forGraph: BindingGraph): InstanceBinding
    val node: NodeModel
}

interface ModuleInstanceInput : ComponentFactoryModel.Input {
    val module: ModuleModel
}
