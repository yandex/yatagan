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
                // TODO: There's a theoretical possibility to use inline strategy here if it can be proven,
                //  that this binding has *a single direct usage as a dependency of a binding of the same scope*.
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

    override fun generate(builder: TypeSpecBuilder) {
        strategies.values.forEach {
            it.generateInComponent(builder)
        }
    }

    fun strategyFor(binding: Binding): AccessStrategy {
        assert(binding.owner == thisGraph)
        return strategies[binding]!!
    }
}