package com.yandex.daggerlite.graph

import com.yandex.daggerlite.core.AssistedInjectFactoryModel
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.CallableLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel

interface ComponentDependencyBinding : Binding {
    val dependency: ComponentDependencyModel
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
interface Binding : BaseBinding {
    val conditionScope: ConditionScope
    val scope: AnnotationLangModel?
    val dependencies: Sequence<NodeDependency>

    fun <R> accept(visitor: Visitor<R>): R

    interface Visitor<R> {
        fun visitProvision(binding: ProvisionBinding): R
        fun visitAssistedInjectFactory(binding: AssistedInjectFactoryBinding): R
        fun visitInstance(binding: InstanceBinding): R
        fun visitAlternatives(binding: AlternativesBinding): R
        fun visitSubComponentFactory(binding: SubComponentFactoryBinding): R
        fun visitComponentDependency(binding: ComponentDependencyBinding): R
        fun visitComponentInstance(binding: ComponentInstanceBinding): R
        fun visitComponentDependencyEntryPoint(binding: ComponentDependencyEntryPointBinding): R
        fun visitMulti(binding: MultiBinding): R
        fun visitEmpty(binding: EmptyBinding): R
    }
}

interface EmptyBinding : Binding

/**
 * A [com.yandex.daggerlite.Provides] binding.
 */
interface ProvisionBinding : Binding {
    val provision: CallableLangModel
    val inputs: List<NodeDependency>
    val requiresModuleInstance: Boolean
}

interface AssistedInjectFactoryBinding : Binding {
    val model: AssistedInjectFactoryModel
}

/**
 * TODO: doc.
 */
interface AlternativesBinding : Binding {
    val alternatives: Sequence<NodeModel>
}

/**
 * A [com.yandex.daggerlite.BindsInstance] binding.
 * Introduced into a graph as [ComponentFactoryModel.InputModel].
 */
interface InstanceBinding : Binding

interface SubComponentFactoryBinding : Binding {
    val targetGraph: BindingGraph
}

interface ComponentInstanceBinding : Binding

interface ComponentDependencyEntryPointBinding : Binding {
    val dependency: ComponentDependencyModel
    val getter: FunctionLangModel
}

interface MultiBinding : Binding {
    val contributions: Map<NodeModel, ContributionType>

    enum class ContributionType {
        /**
         * Single element to be contributed to a collection.
         */
        Element,

        /**
         * Elements of a collection to be contributed.
         */
        Collection,
    }
}