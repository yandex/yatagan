package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.CallableLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel

sealed interface FactoryInputBinding : Binding {
    val input: ComponentFactoryModel.Input
}

interface ComponentDependencyBinding : FactoryInputBinding {
    override val input: ComponentDependencyInput
}

/**
 * A [com.yandex.daggerlite.Binds] binding.
 * Sort of fictional binding, that must be resolved into some real [Binding].
 */
interface AliasBinding : BaseBinding {
    val source: NodeModel
}

/**
 * A base class for all concrete binding implementations, apart from [AliasBinding].
 */
sealed interface Binding : BaseBinding {
    val conditionScope: ConditionScope
    val scope: AnnotationLangModel?
    fun dependencies(): Collection<NodeDependency>
}

interface EmptyBinding : Binding

/**
 * A [com.yandex.daggerlite.Provides] binding.
 */
interface ProvisionBinding : Binding {
    val provision: CallableLangModel
    val inputs: Sequence<NodeDependency>
    val requiresModuleInstance: Boolean
}

/**
 * TODO: doc.
 */
interface AlternativesBinding : Binding {
    val alternatives: Sequence<NodeModel>
}

/**
 * A [com.yandex.daggerlite.BindsInstance] binding.
 * Introduced into a graph as [ComponentFactoryModel.Input].
 */
interface InstanceBinding : FactoryInputBinding {
    override val input: InstanceInput
}

interface SubComponentFactoryBinding : Binding {
    val targetGraph: BindingGraph
}

interface ComponentInstanceBinding : Binding

interface ComponentDependencyEntryPointBinding : Binding {
    val input: ComponentDependencyInput
    val getter: FunctionLangModel
}

interface BootstrapListBinding : Binding {
    val list: Collection<NodeModel>
}