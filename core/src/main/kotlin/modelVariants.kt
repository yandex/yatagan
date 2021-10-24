package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel

class ComponentDependencyFactoryInput(
    override val target: ComponentModel,
    override val paramName: String,
) : Binding(), ComponentFactoryModel.Input

class ModuleInstanceFactoryInput(
    override val target: ModuleModel,
    override val paramName: String,
) : ComponentFactoryModel.Input

/**
 * A [com.yandex.daggerlite.Binds] binding.
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
 * A [com.yandex.daggerlite.Provides] binding.
 */
class ProvisionBinding(
    override val target: NodeModel,
    val scope: AnnotationLangModel?,
    val provider: FunctionLangModel,
    val params: Collection<NodeModel.Dependency>,
    val requiredModuleInstance: ModuleModel?,
) : Binding()

/**
 * A [com.yandex.daggerlite.BindsInstance] binding.
 * Introduced into a graph as [ComponentFactoryModel.Input].
 */
class InstanceBinding(
    override val target: NodeModel,
    override val paramName: String,
) : Binding(), ComponentFactoryModel.Input

class SubComponentFactoryBinding(
    override val target: ComponentFactoryModel,
) : Binding() {
    var targetGraph: BindingGraph by lateInit()
        internal set
}

@Suppress("CanSealedSubClassBeObject")
class ComponentInstanceBinding : Binding() {
    override val target: NodeModel
        get() = owner.model
}

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