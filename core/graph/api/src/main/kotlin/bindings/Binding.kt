package com.yandex.daggerlite.core.graph.bindings

import com.yandex.daggerlite.core.model.ConditionScope
import com.yandex.daggerlite.core.model.NodeDependency
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.lang.Annotation

/**
 * A base class for all concrete binding implementations, apart from [AliasBinding].
 */
interface Binding : BaseBinding {
    /**
     * A condition scope of this binding. Part of the *Conditions API*.
     *
     * If this is [never-scoped][com.yandex.daggerlite.core.model.ConditionExpression.NeverScoped],
     * then [dependencies] **must** yield an empty sequence.
     */
    val conditionScope: ConditionScope

    /**
     * All scopes, that this binding is compatible with (can be cached within).
     * If empty, then the binding is *unscoped* (not cached) and is compatible with *any* scope.
     */
    val scopes: Set<Annotation>

    /**
     * The binding's dependencies on other bindings.
     *
     * If [conditionScope] is ["never"][com.yandex.daggerlite.core.model.ConditionExpression.NeverScoped],
     * then the sequence **must** be empty.
     *
     * Backends can only use bindings that are reported here for codegen/runtime, as the use of any undeclared
     *  dependencies will result in incorrect [BindingUsage][com.yandex.daggerlite.core.graph.BindingGraph.BindingUsage]
     *  computation, internal errors, etc.
     */
    val dependencies: Sequence<NodeDependency>

    /**
     * A set of selected [condition model's roots][com.yandex.daggerlite.core.model.ConditionModel.root] from
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