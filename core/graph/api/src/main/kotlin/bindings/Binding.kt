package com.yandex.yatagan.core.graph.bindings

import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.lang.Annotation

/**
 * A base class for all concrete binding implementations, apart from [AliasBinding].
 */
public interface Binding : BaseBinding {
    /**
     * A condition scope of this binding. Part of the *Conditions API*.
     *
     * If this is [never-scoped][com.yandex.yatagan.core.model.ConditionExpression.NeverScoped],
     * then [dependencies] **must** yield an empty sequence.
     */
    public val conditionScope: ConditionScope

    /**
     * All scopes, that this binding is compatible with (can be cached within).
     * If empty, then the binding is *unscoped* (not cached) and is compatible with *any* scope.
     */
    public val scopes: Set<Annotation>

    /**
     * The binding's dependencies on other bindings.
     *
     * If [conditionScope] is ["never"][com.yandex.yatagan.core.model.ConditionExpression.NeverScoped],
     * then the sequence **must** be empty.
     *
     * Backends can only use bindings that are reported here for codegen/runtime, as the use of any undeclared
     *  dependencies will result in incorrect [BindingUsage][com.yandex.yatagan.core.graph.BindingGraph.BindingUsage]
     *  computation, internal errors, etc.
     */
    public val dependencies: Sequence<NodeDependency>

    /**
     * A set of selected [condition model's roots][com.yandex.yatagan.core.model.ConditionModel.root] from
     * [conditionScope].
     *
     * NOTE: the set may be empty if the binding doesn't _own_ the condition models, e.g. an alternatives binding whose
     * condition scope is inferred from its dependencies and require resolution to be computed.
     */
    public val nonStaticConditionProviders: Set<NodeModel>

    public fun <R> accept(visitor: Visitor<R>): R

    public interface Visitor<R> {
        public fun visitProvision(binding: ProvisionBinding): R
        public fun visitAssistedInjectFactory(binding: AssistedInjectFactoryBinding): R
        public fun visitInstance(binding: InstanceBinding): R
        public fun visitAlternatives(binding: AlternativesBinding): R
        public fun visitSubComponentFactory(binding: SubComponentFactoryBinding): R
        public fun visitComponentDependency(binding: ComponentDependencyBinding): R
        public fun visitComponentInstance(binding: ComponentInstanceBinding): R
        public fun visitComponentDependencyEntryPoint(binding: ComponentDependencyEntryPointBinding): R
        public fun visitMulti(binding: MultiBinding): R
        public fun visitMap(binding: MapBinding): R
        public fun visitEmpty(binding: EmptyBinding): R
    }
}