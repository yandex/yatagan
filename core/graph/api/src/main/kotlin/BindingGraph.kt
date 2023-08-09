/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.core.graph

import com.yandex.yatagan.core.graph.BindingGraph.LiteralUsage.Eager
import com.yandex.yatagan.core.graph.BindingGraph.LiteralUsage.Lazy
import com.yandex.yatagan.core.graph.bindings.BaseBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.AssistedInjectFactoryModel
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ComponentModel
import com.yandex.yatagan.core.model.ConditionModel
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.ScopeModel
import com.yandex.yatagan.core.model.Variant
import com.yandex.yatagan.validation.MayBeInvalid

/**
 * A Fully built Yatagan graph of [Binding]s.
 *
 * Each [BindingGraph] is built around [ComponentModel]. For each given [ComponentModel] multiple different
 * [BindingGraph]s may exist if [ComponentModel.isRoot] is `false`, because the model may have different parents.
 */
public interface BindingGraph : MayBeInvalid, Extensible, WithParents<BindingGraph>, WithChildren<BindingGraph> {
    /**
     * A model behind this graph.
     */
    public val model: ComponentModel

    /**
     * Requested bindings that are **hosted** in this component.
     * Consists of bindings directly requested by entryPoints plus bindings requested by sub-graphs.
     *
     * Thus, every binding that is present in this collection has it [owner][Binding.owner] set to `this` graph.
     *
     * The associated info is [BindingUsage].
     */
    public val localBindings: Map<Binding, BindingUsage>

    /**
     * All [condition variables][com.yandex.yatagan.core.model.BooleanExpression.Variable]s that are **hosted** in this
     * component. Consists of literals directly used by bindings in this and children graphs.
     *
     * The associated info is [LiteralUsage].
     */
    public val localConditionLiterals: Map<ConditionModel, LiteralUsage>

    /**
     * [AssistedInjectFactoryModel]s that are hosted in this graph.
     */
    public val localAssistedInjectFactories: Collection<AssistedInjectFactoryModel>

    /**
     * A collection of parent (not necessarily direct) [BindingGraph]s, from which bindings and/or conditions are used
     * to satisfy dependencies from `this` graph.
     */
    public val usedParents: Set<BindingGraph>

    /**
     * See [ComponentModel.isRoot].
     */
    public val isRoot: Boolean

    /**
     * Graph variant (full - merged with parents)
     *
     * @see ComponentModel.variant
     */
    public val variant: Variant

    /**
     * Modules of the underlying model.
     *
     * @see ComponentModel.modules
     */
    public val modules: Collection<ModuleModel>

    /**
     * Component dependencies declared in the underlying model.
     *
     * @see ComponentModel.dependencies
     */
    public val dependencies: Collection<ComponentDependencyModel>

    /**
     * Graph scopes.
     *
     * @see ComponentModel.scopes
     */
    public val scopes: Set<ScopeModel>

    /**
     * Component creator [model][ComponentModel.factory] declared in the underlying model
     * or the child component factory [method][ComponentModel.subComponentFactoryMethods]
     * declared in the parent component model.
     *
     * @see [ComponentModel.factory]
     * @see [ComponentModel.subComponentFactoryMethods]
     * @see [subComponentFactoryMethods]
     */
    public val creator: ComponentFactoryModel?

    /**
     * All explicit graph entry-points - getter functions declared in the underlying model.
     *
     * @see [ComponentModel.entryPoints]
     */
    public val entryPoints: Collection<GraphEntryPoint>

    /**
     * All injector functions (a kind of entry-points) declared in the underlying model.
     *
     * @see [ComponentModel.memberInjectors]
     */
    public val memberInjectors: Collection<GraphMemberInjector>

    /**
     * @see ComponentModel.subComponentFactoryMethods
     */
    public val subComponentFactoryMethods: Collection<GraphSubComponentFactoryMethod>

    /**
     * A condition for this graph.
     *
     * Equals [Always][com.yandex.yatagan.core.model.ConditionScope.Always] for [root][BindingGraph.isRoot] components.
     * Arbitrary for non-root components.
     */
    public val conditionScope: ConditionScope

    /**
     * Multi-thread access status declared in the underlying model.
     *
     * @see ComponentModel.requiresSynchronizedAccess
     */
    public val requiresSynchronizedAccess: Boolean

    /**
     * Resolves binding for the given node. Resulting binding may belong to this graph or any parent one.
     *
     * @return resolved binding (it may be [com.yandex.yatagan.core.graph.bindings.EmptyBinding] due to
     * [NeverScoped][com.yandex.yatagan.core.model.ConditionScope.Never] or because it was requested and
     * could not be satisfied)
     *
     * @throws IllegalStateException if no such binding is found at all (requested but missing bindings are still safe
     * to request).
     */
    public fun resolveBinding(node: NodeModel): Binding

    /**
     * Behaves as [resolveBinding] only doesn't follow aliases.
     */
    public fun resolveBindingRaw(node: NodeModel): BaseBinding

    /**
     * Provides counts of each dependency [kind][com.yandex.yatagan.core.DependencyKind] requests for the
     * [target][Binding.target].
     */
    public interface BindingUsage {
        public val direct: Int
        public val provider: Int
        public val lazy: Int
        public val optional: Int
        public val optionalLazy: Int
        public val optionalProvider: Int
    }

    /**
     * Literal usage kind depending on expression position.
     *
     * In expression `A && B || C`, `A` is [Eager], while `B`, `C` are [Lazy].
     * In other words, a literal is [Eager] iff it is present in eager position at in at least one expression.
     */
    public enum class LiteralUsage {
        /**
         * When a literal is certainly going to be evaluated.
         */
        Eager,

        /**
         * When a literal evaluation may be skipped due to a short-circuit expression evaluation.
         * Such literal should be evaluated according to the short-circuit rules.
         */
        Lazy,
    }
}
