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

package com.yandex.yatagan.core.graph.impl.bindings

import com.yandex.yatagan.base.notIntersects
import com.yandex.yatagan.core.graph.bindings.BaseBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.impl.NonStaticConditionDependencies
import com.yandex.yatagan.core.graph.impl.VariantMatch
import com.yandex.yatagan.core.graph.impl.contains
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.ModuleHostedBindingModel
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import com.yandex.yatagan.core.model.isOptional
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError

internal interface BaseBindingDefaultsMixin : BaseBinding {
    override val originModule: ModuleModel?
        get() = null
}

internal interface BindingDefaultsMixin : Binding, BaseBindingDefaultsMixin {
    override val conditionScope: ConditionScope
        get() = ConditionScope.Unscoped

    override val nonStaticConditionProviders: Set<NodeModel>
        get() = emptySet()

    override val scopes: Set<Annotation>
        get() = emptySet()

    val nonStaticConditionDependencies: NonStaticConditionDependencies?
        get() = null

    /**
     * `true` if the binding requires the dependencies of compatible condition scope, and it's an error to
     *  provide a dependency under incompatible condition.
     *
     *  `false` if the binding allows dependencies of incompatible scope, e.g. because it can just skip them.
     */
    val checkDependenciesConditionScope: Boolean get() = false

    override fun validate(validator: Validator) {
        dependencies.forEach {
            validator.child(owner.resolveBindingRaw(it.node))
        }
        nonStaticConditionDependencies?.let(validator::child)

        if (checkDependenciesConditionScope) {
            val conditionScope = graphConditionScope()
            for (dependency in dependencies) {
                val (node, kind) = dependency
                if (kind.isOptional) continue
                val resolved = owner.resolveBinding(node)
                val resolvedScope = resolved.graphConditionScope()
                if (resolvedScope !in conditionScope) {
                    // Incompatible condition!
                    validator.reportError(Strings.Errors.incompatibleCondition(
                        aCondition = resolvedScope,
                        bCondition = conditionScope,
                        a = resolved,
                        b = this,
                    )) {
                        val aliases = owner.resolveAliasChain(node)
                        if (aliases.isNotEmpty()) {
                            addNote(Strings.Notes.conditionPassedThroughAliasChain(aliases = aliases))
                        }
                    }
                }
            }
        }

        if (scopes.isNotEmpty() && scopes notIntersects owner.scopes) {
            validator.reportError(Strings.Errors.noMatchingScopeForBinding(binding = this, scopes = scopes))
        }
    }

    override val dependencies: Sequence<NodeDependency>
        get() = emptySequence()

    override fun <R> accept(visitor: BaseBinding.Visitor<R>): R {
        return visitor.visitBinding(this)
    }
}

internal interface ConditionalBindingMixin : BindingDefaultsMixin {
    val variantMatch: VariantMatch

    override val nonStaticConditionDependencies: NonStaticConditionDependencies

    override val nonStaticConditionProviders: Set<NodeModel>
        get() = nonStaticConditionDependencies.conditionProviders

    override val conditionScope: ConditionScope
        get() = variantMatch.conditionScope

    override fun validate(validator: Validator) {
        super.validate(validator)
        when (val match = variantMatch) {
            is VariantMatch.Error -> match.message?.let { error -> validator.report(error) }
            is VariantMatch.Matched -> {}
        }
    }
}

internal sealed interface ComparableBindingMixin<B : ComparableBindingMixin<B>> : BaseBinding {
    fun compareTo(other: B): Int

    override fun compareTo(other: BaseBinding): Int {
        if (this == other) return 0
        other as ComparableBindingMixin<*>

        orderByClass(this).compareTo(orderByClass(other)).let { if (it != 0) return it }

        // If the class order is the same, assume the class is matching, delegate to dedicated comparison.
        @Suppress("UNCHECKED_CAST")
        return compareTo(other as B)
    }

    private companion object {
        // NOTE: These priorities denote a default order for contributions in a multi-binding - do not change these.
        fun orderByClass(binding: ComparableBindingMixin<*>): Int = when(binding) {
            // Usable bindings - the order number should be stable.
            is ModuleHostedBindingMixin -> 0
            is InjectConstructorProvisionBindingImpl -> 10
            is AssistedInjectFactoryBindingImpl -> 40
            is ComponentDependencyBindingImpl -> 50
            is ComponentDependencyEntryPointBindingImpl -> 60
            is ComponentInstanceBindingImpl -> 70
            is SubComponentFactoryBindingImpl -> 80
            is InstanceBindingImpl -> 90
            is MapBindingImpl -> 100
            is MultiBindingImpl -> 110
            // Invalid bindings, don't really care for their order - it's UB to use them anyway.
            is MissingBindingImpl -> Int.MAX_VALUE - 1
            is AliasLoopStubBinding -> Int.MAX_VALUE - 2
        }
    }
}

internal sealed interface ComparableByTargetBindingMixin : ComparableBindingMixin<ComparableByTargetBindingMixin> {
    override fun compareTo(other: ComparableByTargetBindingMixin): Int {
        target.compareTo(other.target).let { if (it != 0) return it }
        // Fallback to compare owners in very rare case targets are equal
        return owner.model.type.compareTo(other.owner.model.type)
    }
}

internal abstract class ModuleHostedBindingMixin :
    BaseBindingDefaultsMixin, ComparableBindingMixin<ModuleHostedBindingMixin> {
    abstract val impl: ModuleHostedBindingModel

    final override val originModule get() = impl.originModule

    final override val target: NodeModel by lazy(LazyThreadSafetyMode.PUBLICATION) {
        when (val target = impl.target) {
            is ModuleHostedBindingModel.BindingTargetModel.DirectMultiContribution,
            is ModuleHostedBindingModel.BindingTargetModel.FlattenMultiContribution,
            is ModuleHostedBindingModel.BindingTargetModel.MappingContribution,
            -> MultiBindingContributionNode(target.node)

            is ModuleHostedBindingModel.BindingTargetModel.Plain -> target.node
        }
    }

    private class MultiBindingContributionNode(
        private val underlying: NodeModel,
    ) : NodeModel by underlying {
        override fun getSpecificModel(): Nothing? = null
        override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
            modelClassName = "multi-binding-contributor",
            representation = underlying.toString(childContext = null),
        )

        override val node: NodeModel get() = this
    }

    final override fun compareTo(other: ModuleHostedBindingMixin): Int {
        return impl.method.compareTo(other.impl.method)
    }
}