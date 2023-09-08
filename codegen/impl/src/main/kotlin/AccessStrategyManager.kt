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

package com.yandex.yatagan.codegen.impl

import com.yandex.yatagan.AssistedFactory
import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.ScopeModel
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class AccessStrategyManager @Inject constructor(
    private val thisGraph: BindingGraph,
    private val options: ComponentGenerator.Options,
    private val cachingStrategySingleThreadFactory: CachingStrategySingleThreadFactory,
    private val cachingStrategyMultiThreadFactory: CachingStrategyMultiThreadFactory,
    private val slotProviderStrategyFactory: SlotProviderStrategyFactory,
    private val wrappingAccessorStrategyFactory: WrappingAccessorStrategyFactory,
    private val onTheFlyScopedProviderCreationStrategyFactory: OnTheFlyScopedProviderCreationStrategyFactory,
    private val onTheFlyUnscopedProviderCreationStrategyFactory: OnTheFlyUnscopedProviderCreationStrategyFactory,
    private val conditionalAccessStrategyFactory: ConditionalAccessStrategyFactory,
) : ComponentGenerator.Contributor {

    @AssistedFactory
    interface CachingStrategySingleThreadFactory {
        fun create(binding: Binding): CachingStrategySingleThread
    }

    @AssistedFactory
    interface CachingStrategyMultiThreadFactory {
        fun create(binding: Binding): CachingStrategyMultiThread
    }

    @AssistedFactory
    interface SlotProviderStrategyFactory {
        fun create(binding: Binding): SlotProviderStrategy
    }

    @AssistedFactory
    interface OnTheFlyScopedProviderCreationStrategyFactory {
        fun create(binding: Binding): OnTheFlyScopedProviderCreationStrategy
    }

    @AssistedFactory
    interface OnTheFlyUnscopedProviderCreationStrategyFactory {
        fun create(binding: Binding): OnTheFlyUnscopedProviderCreationStrategy
    }

    @AssistedFactory
    interface WrappingAccessorStrategyFactory {
        fun create(binding: Binding, underlying: AccessStrategy): WrappingAccessorStrategy
    }

    @AssistedFactory
    interface ConditionalAccessStrategyFactory {
        fun create(
            binding: Binding,
            underlying: AccessStrategy,
            dependencyKind: DependencyKind,
        ): ConditionalAccessStrategy
    }

    /**
     * A mapping between a local binding and its single dependent binding, if any.
     * Required for inline optimization calculations.
     */
    private val singleLocalDependentBindingCache: Map<Binding, Binding?> =
        buildMap(thisGraph.localBindings.size) {
            for (binding in thisGraph.localBindings.keys) {
                val dependencies = if (binding.nonStaticConditionProviders.isNotEmpty()) {
                    binding.dependencies + binding.nonStaticConditionProviders
                } else binding.dependencies

                for ((node, _) in dependencies) {
                    val dependencyBinding = thisGraph.resolveBinding(node)
                        .takeIf { it.owner == thisGraph } ?: continue
                    if (dependencyBinding in this) {
                        // Not the first dependent binding, explicitly put `null` there,
                        //  as it is no longer single
                        put(dependencyBinding, null)
                    } else {
                        put(dependencyBinding, binding)
                    }
                }
            }
        }

    private val strategies: Map<Binding, AccessStrategy> = thisGraph.localBindings.entries.associateBy(
        keySelector = { (binding, _) -> binding },
        valueTransform = fun(entry: Map.Entry<Binding, BindingGraph.BindingUsage>): AccessStrategy {
            val (binding, usage) = entry
            if (binding.conditionScope == ConditionScope.Never) {
                return EmptyAccessStrategy
            }
            val strategy = if (binding.scopes.isNotEmpty()) {
                when(detectDegenerateUsage(binding)) {
                    DegenerateBindingUsage.SingleLocalDirect -> {
                        inlineStrategy(
                            binding = binding,
                            usage = usage,
                        )
                    }
                    DegenerateBindingUsage.SingleLocalProvider,
                    DegenerateBindingUsage.SingleLocalLazy -> {
                        CompositeStrategy(
                            directStrategy = inlineStrategy(
                                binding = binding,
                                usage = usage,
                            ),
                            lazyStrategy = onTheFlyScopedProviderCreationStrategyFactory.create(binding),
                        )
                    }
                    null -> {
                        val useMultiThreadCaching =
                            thisGraph.requiresSynchronizedAccess && ScopeModel.Reusable !in binding.scopes
                        CompositeStrategy(
                            directStrategy = if (useMultiThreadCaching) {
                                cachingStrategyMultiThreadFactory.create(binding)
                            } else {
                                cachingStrategySingleThreadFactory.create(binding)
                            },
                            lazyStrategy = if (usage.lazy + usage.provider > 0) {
                                slotProviderStrategyFactory.create(binding)
                            } else null
                        )
                    }
                }
            } else {
                if (usage.lazy + usage.provider == 0) {
                    // Inline instance creation does the trick.
                    inlineStrategy(
                        binding = binding,
                        usage = usage,
                    )
                } else {
                    CompositeStrategy(
                        directStrategy = inlineStrategy(
                            binding = binding,
                            usage = usage,
                        ),
                        lazyStrategy = if (usage.lazy > 0) {
                            onTheFlyScopedProviderCreationStrategyFactory.create(binding)
                        } else null,
                        providerStrategy = if (usage.provider > 0) {
                            onTheFlyUnscopedProviderCreationStrategyFactory.create(binding)
                        } else null,
                    )
                }
            }
            return if (usage.optional > 0) {
                fun conditionalStrategy(kind: DependencyKind): ConditionalAccessStrategy {
                    return conditionalAccessStrategyFactory.create(
                        underlying = strategy,
                        binding = binding,
                        dependencyKind = kind,
                    )
                }
                CompositeStrategy(
                    directStrategy = strategy,
                    conditionalStrategy = conditionalStrategy(DependencyKind.Direct),
                    conditionalLazyStrategy = if (usage.optionalLazy > 0)
                        conditionalStrategy(DependencyKind.Lazy) else null,
                    conditionalProviderStrategy = if (usage.optionalProvider > 0)
                        conditionalStrategy(DependencyKind.Provider) else null,
                )
            } else strategy
        },
    )

    override fun generate(builder: TypeSpecBuilder) {
        strategies.entries.let { strategies ->
            if (options.sortMethodsForTesting) strategies.sortedBy { it.key } else strategies
        }.forEach { (_, strategy) ->
            strategy.generateInComponent(builder)
        }
    }

    fun strategyFor(binding: Binding): AccessStrategy {
        assert(binding.owner == thisGraph)
        return strategies[binding]!!
    }

    enum class DegenerateBindingUsage {
        SingleLocalDirect,
        SingleLocalLazy,
        SingleLocalProvider,
    }

    private fun detectDegenerateUsage(
        binding: Binding,
        usage: BindingGraph.BindingUsage = thisGraph.localBindings[binding]!!
    ): DegenerateBindingUsage? {
        // There's a possibility to use inline strategy (create on request) for scoped node if it can be proven,
        // that the binding has *a single eager (direct/optional) usage as a dependency of a binding of the same
        // scope*.

        val potentialCase = with(usage) {
            val directUsage = direct + optional
            val providerUsage = provider + optionalProvider
            val lazyUsage = lazy + optionalLazy
            when {
                directUsage == 1 && lazyUsage + providerUsage == 0 -> DegenerateBindingUsage.SingleLocalDirect
                directUsage == 0 -> when {
                    lazyUsage == 0 && providerUsage == 1 -> DegenerateBindingUsage.SingleLocalProvider
                    lazyUsage == 1 && providerUsage == 0 -> DegenerateBindingUsage.SingleLocalLazy
                    else -> return null
                }
                else -> return null
            }
        }

        val singleDependentBinding = singleLocalDependentBindingCache[binding] ?: return null

        // Now we know for sure that the dependent binding is *single*.
        // We're going to rustle through localBindings of the binding's owner graph in hope to find this single
        //  dependant binding. If we find it - cool, can use inline. Otherwise, this single dependant may be
        //  from the child graphs or entry-point(s)/member-injector(s), etc... - not correct to use inline.

        if (singleDependentBinding.scopes.let {
                // If it is scoped (cached), can use the optimization
                it.isNotEmpty() &&
                        // If the binding is Reusable and the component requires MT-access,
                        // then we can't use the optimization as it *might* be created multiple times in contended
                        // MT environments - can't risk that.
                        (!singleDependentBinding.owner.requiresSynchronizedAccess || ScopeModel.Reusable !in it)
            }) {
            return potentialCase
        }

        // For unscoped dependent try to analyze transitively, if it's also only created once
        return when (detectDegenerateUsage(singleDependentBinding)) {
            DegenerateBindingUsage.SingleLocalDirect,
            DegenerateBindingUsage.SingleLocalLazy
            -> {
                // The unscoped class itself will be created at most once, so it's ok to use optimization
                potentialCase
            }
            DegenerateBindingUsage.SingleLocalProvider, null -> {
                // Single provider for unscoped node can spawn multiple instances, no optimization
                null
            }
        }
    }

    private fun inlineStrategy(
        binding: Binding,
        usage: BindingGraph.BindingUsage,
    ): AccessStrategy {
        val inline = InlineCreationStrategy(
            binding = binding,
        )

        if (binding.dependencies.isEmpty())
            return inline
        if (usage.provider + usage.lazy == 0) {
            if (usage.direct == 1) {
                return inline
            }
        } else {
            if (usage.direct == 0) {
                return inline
            }
        }

        return wrappingAccessorStrategyFactory.create(
            underlying = inline,
            binding = binding,
        )
    }
}