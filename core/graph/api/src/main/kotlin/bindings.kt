package com.yandex.daggerlite.core.graph

import com.yandex.daggerlite.core.model.AssistedInjectFactoryModel
import com.yandex.daggerlite.core.model.CollectionTargetKind
import com.yandex.daggerlite.core.model.ComponentDependencyModel
import com.yandex.daggerlite.core.model.ComponentFactoryModel
import com.yandex.daggerlite.core.model.ConditionScope
import com.yandex.daggerlite.core.model.NodeDependency
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.lang.AnnotationLangModel
import com.yandex.daggerlite.lang.CallableLangModel
import com.yandex.daggerlite.lang.FunctionLangModel
import com.yandex.daggerlite.lang.TypeLangModel

interface ComponentDependencyBinding : Binding {
    val dependency: ComponentDependencyModel
}

/**
 * A [com.yandex.daggerlite.Binds] binding.
 * Sort of fictional binding, that must be resolved into some real [Binding].
 */
interface AliasBinding : BaseBinding {
    /**
     * Actual implementation node to be used (aliased for) for compatible [target].
     */
    val source: NodeModel
}

/**
 * A base class for all concrete binding implementations, apart from [AliasBinding].
 */
interface Binding : BaseBinding {
    /**
     * A condition scope of this binding. Part of the *Conditions API*.
     *
     * If this is [never-scoped][com.yandex.daggerlite.core.ConditionExpression.NeverScoped],
     * then [dependencies] **must** yield an empty sequence.
     */
    val conditionScope: ConditionScope

    /**
     * All scopes, that this binding is compatible with (can be cached within).
     * If empty, then the binding is *unscoped* (not cached) and is compatible with *any* scope.
     */
    val scopes: Set<AnnotationLangModel>

    /**
     * The binding's dependencies on other bindings.
     *
     * If [conditionScope] is ["never"][com.yandex.daggerlite.core.ConditionExpression.NeverScoped],
     * then the sequence **must** be empty.
     *
     * Backends can only use bindings that are reported here for codegen/runtime, as the use of any undeclared
     *  dependencies will result in incorrect [BindingGraph.BindingUsage] computation, internal errors, etc.
     */
    val dependencies: Sequence<NodeDependency>

    /**
     * A set of selected [condition model's roots][com.yandex.daggerlite.core.ConditionModel.root] from
     * [conditionScope].
     *
     * NOTE: the set may be empty if the binding doesn't _own_ the condition models, e.g. an alternatives binding whose
     * condition scope is inferred from its dependencies and require resolution to be computed.
     */
    val nonStaticConditionProviders: Set<NodeModel>

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
        fun visitMap(binding: MapBinding): R
        fun visitEmpty(binding: EmptyBinding): R
    }
}

/**
 * Binding that can not be satisfied - it's codegen or runtime evaluation is *unreached*.
 *
 * Empty [com.yandex.daggerlite.Binds] binding is an example of such binding.
 */
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
 * A specific case of [com.yandex.daggerlite.Binds] binding with multiple alternatives.
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

/**
 * A binding that can override/extend a binding with the same [target] from the parent graph.
 */
interface ExtensibleBinding<B> : Binding where B : ExtensibleBinding<B> {
    /**
     * An optional reference to a binding from one of the parent graphs, to include contributions from.
     */
    val upstream: B?

    /**
     * A special intrinsic node, which is used for downstream binding to depend on this binding
     *  (as its [upstream]).
     *
     * Any downstream bindings' dependencies must include this node.
     */
    val targetForDownstream: NodeModel
}

/**
 * A binding for a `List<T>` aggregating all contributions for `T`, marked with [com.yandex.daggerlite.IntoList].
 * Such bindings exhibit "extends" behavior: bindings for the same list in child graphs inherit all the contributions
 *  from parent ones (in a cascading way) and are not considered conflicting.
 */
interface MultiBinding : ExtensibleBinding<MultiBinding> {
    /**
     * All list contributions.
     */
    val contributions: Map<NodeModel, ContributionType>

    /**
     * Target collection kind.
     */
    val kind: CollectionTargetKind

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

interface MapBinding : ExtensibleBinding<MapBinding> {
    val mapKey: TypeLangModel
    val mapValue: TypeLangModel

    /**
     * NOTE: Dependency resolve should be done exactly on the binding's [owner].
     */
    val contents: Collection<Contribution>

    /**
     * A pair of [keyValue], [dependency] to be put into map.
     */
    interface Contribution {
        /**
         * A value of a key annotation (read: map key)
         */
        val keyValue: AnnotationLangModel.Value

        /**
         * A dependency which resolves to a contribution for the key.
         */
        val dependency: NodeDependency
    }
}
