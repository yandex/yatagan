package com.yandex.daggerlite.core

class ComponentDependencyFactoryInput(
    override val target: ComponentModel,
    override val paramName: String,
) : Binding(), ComponentFactoryModel.Input

class ModuleInstanceFactoryInput(
    override val target: ModuleModel,
    override val paramName: String,
) : ComponentFactoryModel.Input

/**
 * A [dagger.Binds] binding.
 * Sort of fictional binding, that must be resolved into some real [Binding].
 */
class AliasBinding(
    override val target: NodeModel,
    val source: NodeModel,
) : BaseBinding()

/**
 * A base class for all concrete binding implementations, apart from [AliasBinding].
 */
sealed class Binding : BaseBinding()

/**
 * A [dagger.Provides] binding.
 */
class ProvisionBinding(
    override val target: NodeModel,
    val scope: Scope?,
    val provider: CallableNameModel,
    val params: Collection<NodeModel.Dependency>,
) : Binding() {
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
) : Binding(), ComponentFactoryModel.Input

class SubComponentFactoryBinding(
    override val target: ComponentFactoryModel,
) : Binding()

class ComponentInstanceBinding(
    override val target: ComponentModel,
) : Binding()

class DependencyComponentEntryPointBinding(
    val input: ComponentDependencyFactoryInput,
    val entryPoint: ComponentModel.EntryPoint,
) : Binding() {
    init {
        require(entryPoint.dependency.kind == NodeModel.Dependency.Kind.Direct) {
            // MAYBE: Implement some best-effort matching to available dependency kinds?
            "Only direct entry points constitute a binding that can be used in dependency components"
        }
    }

    override val target: NodeModel
        get() = entryPoint.dependency.node
}