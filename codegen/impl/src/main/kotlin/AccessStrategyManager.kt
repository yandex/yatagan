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

import com.yandex.yatagan.codegen.poetry.TypeSpecBuilder
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.Extensible
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import com.yandex.yatagan.core.model.isNever

internal class AccessStrategyManager(
    private val thisGraph: BindingGraph,
    fieldsNs: Namespace,
    methodsNs: Namespace,
    multiFactory: Provider<SlotSwitchingGenerator>,
    unscopedProviderGenerator: Provider<UnscopedProviderGenerator>,
    scopedProviderGenerator: Provider<ScopedProviderGenerator>,
    lockGenerator: Provider<LockGenerator>,
) : ComponentGenerator.Contributor {

    /**
     * A mapping between a local binding and its single dependent binding, if any.
     * Required for inline optimization calculations.
     */
    private val singleLocalDependentBindingCache: Map<Binding, Binding?> =
        buildMap(thisGraph.localBindings.size) {
            val allLocalNodes: Map<NodeModel, Binding> = thisGraph.localBindings.keys.associateBy(Binding::target)
            for (binding in thisGraph.localBindings.keys) {

                val dependencies = if (binding.nonStaticConditionProviders.isNotEmpty()) {
                    binding.dependencies + binding.nonStaticConditionProviders
                } else binding.dependencies

                for ((node, _) in dependencies) {
                    val dependencyBinding = allLocalNodes[node] ?: continue
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
            if (binding.conditionScope.isNever) {
                return EmptyAccessStrategy
            }
            val strategy = if (binding.scopes.isNotEmpty()) {
                when(detectDegenerateUsage(binding)) {
                    DegenerateBindingUsage.SingleLocalDirect -> {
                        inlineStrategy(
                            binding = binding,
                            usage = usage,
                            methodsNs = methodsNs,
                        )
                    }
                    DegenerateBindingUsage.SingleLocalProvider,
                    DegenerateBindingUsage.SingleLocalLazy -> {
                        val slot = multiFactory.get().requestSlot(binding)
                        CompositeStrategy(
                            directStrategy = inlineStrategy(
                                binding = binding,
                                usage = usage,
                                methodsNs = methodsNs,
                            ),
                            lazyStrategy = OnTheFlyScopedProviderCreationStrategy(
                                cachingProvider = scopedProviderGenerator.get(),
                                binding = binding,
                                slot = slot,
                            ),
                        )
                    }
                    null -> {
                        CompositeStrategy(
                            directStrategy = if (thisGraph.requiresSynchronizedAccess) {
                                CachingStrategyMultiThread(
                                    binding = binding,
                                    fieldsNs = fieldsNs,
                                    methodsNs = methodsNs,
                                    lock = lockGenerator.get(),
                                )
                            } else {
                                CachingStrategySingleThread(
                                    binding = binding,
                                    fieldsNs = fieldsNs,
                                    methodsNs = methodsNs,
                                )
                            },
                            lazyStrategy = if (usage.lazy + usage.provider > 0) {
                                SlotProviderStrategy(
                                    binding = binding,
                                    slot = multiFactory.get().requestSlot(binding),
                                    cachingProvider = unscopedProviderGenerator.get(),
                                )
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
                        methodsNs = methodsNs,
                    )
                } else {
                    val slot = multiFactory.get().requestSlot(binding)
                    CompositeStrategy(
                        directStrategy = inlineStrategy(
                            binding = binding,
                            usage = usage,
                            methodsNs = methodsNs,
                        ),
                        lazyStrategy = if (usage.lazy > 0) OnTheFlyScopedProviderCreationStrategy(
                            cachingProvider = scopedProviderGenerator.get(),
                            binding = binding,
                            slot = slot,
                        ) else null,
                        providerStrategy = if (usage.provider > 0) OnTheFlyUnscopedProviderCreationStrategy(
                            unscopedProviderGenerator = unscopedProviderGenerator.get(),
                            binding = binding,
                            slot = slot,
                        ) else null,
                    )
                }
            }
            return if (usage.optional > 0) {
                fun conditionalStrategy(kind: DependencyKind): ConditionalAccessStrategy {
                    return ConditionalAccessStrategy(
                        underlying = strategy,
                        binding = binding,
                        methodsNs = methodsNs,
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
        strategies.values.forEach {
            it.generateInComponent(builder)
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

        if (singleDependentBinding.scopes.isNotEmpty()) {
            // If it is scoped (cached), can use the optimization
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
        methodsNs: Namespace,
    ): AccessStrategy {
        val inline = InlineCreationStrategy(
            binding = binding,
        )

        if (binding.dependencies.none())
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

        return WrappingAccessorStrategy(
            underlying = inline,
            binding = binding,
            methodsNs = methodsNs,
        )
    }

    companion object Key : Extensible.Key<AccessStrategyManager> {
        override val keyType get() = AccessStrategyManager::class.java
    }
}