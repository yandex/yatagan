package com.yandex.daggerlite.core

class ComponentDependencyFactoryInput(
    override val target: ComponentModel,
    override val paramName: String,
) : ComponentFactoryModel.Input

class ModuleInstanceFactoryInput(
    override val target: ModuleModel,
    override val paramName: String,
) : ComponentFactoryModel.Input

/**
 * A [dagger.Binds] binding.
 */
class AliasBinding(
    override val target: NodeModel,
    val source: NodeModel,
) : Binding()

sealed class NonAliasBinding : Binding()

/**
 * A [dagger.Provides] binding.
 */
class ProvisionBinding(
    override val target: NodeModel,
    val scope: Scope?,
    val provider: CallableNameModel,
    val params: Collection<NodeModel.Dependency>,
) : NonAliasBinding() {
    /**
     * Represent provision cache scope.
     * Must provide [equals]/[hashCode] implementation.
     */
    interface Scope
}

/**
 * A [dagger.BindsInstance] binding.
 * Introduced into a graph as [ComponentFactoryModel.Input].
 */
class InstanceBinding(
    override val target: NodeModel,
    override val paramName: String,
) : NonAliasBinding(), ComponentFactoryModel.Input

class SubComponentFactoryBinding(
    override val target: ComponentFactoryModel,
) : NonAliasBinding()

class ComponentInstanceBinding(
    override val target: ComponentModel,
) : NonAliasBinding()