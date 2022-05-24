package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import javax.inject.Provider

internal class AccessStrategyManager(
    private val thisGraph: BindingGraph,
    fieldsNs: Namespace,
    methodsNs: Namespace,
    multiFactory: Provider<SlotSwitchingGenerator>,
    unscopedProviderGenerator: Provider<UnscopedProviderGenerator>,
    scopedProviderGenerator: Provider<ScopedProviderGenerator>,
    lockGenerator: Provider<LockGenerator>,
) : ComponentGenerator.Contributor {

    private val strategies: Map<Binding, AccessStrategy> = thisGraph.localBindings.entries.associateBy(
        keySelector = { (binding, _) -> binding },
        valueTransform = fun(entry: Map.Entry<Binding, BindingGraph.BindingUsage>): AccessStrategy {
            val (binding, usage) = entry
            if (binding.conditionScope.isNever) {
                return EmptyAccessStrategy
            }
            val strategy = if (binding.scope != null) {
                when(detectDegenerateUsage(binding, usage)) {
                    ScopedDegenerateUsage.CanUseInline -> {
                        inlineStrategy(
                            binding = binding,
                            usage = usage,
                            methodsNs = methodsNs,
                        )
                    }
                    ScopedDegenerateUsage.CanUseOnTheFlyProvider -> {
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
                                    multiFactory = multiFactory.get(),
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

    enum class ScopedDegenerateUsage {
        CanUseInline,
        CanUseOnTheFlyProvider,
    }

    companion object {
        private fun inlineStrategy(
            binding: Binding,
            usage: BindingGraph.BindingUsage,
            methodsNs: Namespace,
        ): AccessStrategy {
            val inline = InlineCreationStrategy(
                binding = binding,
            )

            if (binding.dependencies().isEmpty())
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

        private fun detectDegenerateUsage(binding: Binding, usage: BindingGraph.BindingUsage): ScopedDegenerateUsage? {
            // There's a possibility to use inline strategy (create on request) for scoped node if it can be proven,
            // that the binding has *a single eager (direct/optional) usage as a dependency of a binding of the same
            // scope*.

            val potentialCase = with(usage) {
                when {
                    direct + optional == 1 && lazy + provider + optionalLazy + optionalProvider == 0 ->
                        ScopedDegenerateUsage.CanUseInline
                    direct + optional == 0 && lazy + provider + optionalLazy + optionalProvider == 1 ->
                        ScopedDegenerateUsage.CanUseOnTheFlyProvider
                    else -> return null
                }
            }

            // Now we know for sure that the dependent binding is *single*.
            // We're going to rustle through localBindings of the binding's owner graph in hope to find this single
            //  dependant binding. If we find it - cool, can use inline. Otherwise, this single dependant may be
            //  from the child graphs or entry-point(s)/member-injector(s), etc... - not correct to use inline.
            for (localBinding in binding.owner.localBindings.keys) {
                for ((dependencyNode, _) in localBinding.dependencies()) {
                    if (binding.target == dependencyNode) {
                        // Found this single dependent binding locally => it has the same scope, nice.
                        return potentialCase
                    }
                }
            }
            return null
        }
    }
}