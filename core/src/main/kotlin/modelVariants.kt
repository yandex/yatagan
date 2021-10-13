package com.yandex.dagger3.core

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
    override val scope: Binding.Scope?,
    val source: NodeModel,
) : Binding

sealed interface NonAliasBinding : Binding

/**
 * A [dagger.Provides] binding.
 */
class ProvisionBinding(
    override val target: NodeModel,
    override val scope: Binding.Scope?,
    val provider: CallableNameModel,
    val params: Collection<NodeModel.Dependency>,
) : NonAliasBinding

/**
 * A [dagger.BindsInstance] binding.
 * Introduced into a graph as [ComponentFactoryModel.Input].
 */
class InstanceBinding(
    override val target: NodeModel,
    override val paramName: String,
) : NonAliasBinding, ComponentFactoryModel.Input {
    /**
     * No scope - no need to cache ready instance.
     */
    override val scope: Nothing? get() = null
}