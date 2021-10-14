package com.yandex.dagger3.generator

import com.yandex.dagger3.core.BindingGraph
import com.yandex.dagger3.core.InstanceBinding
import com.yandex.dagger3.core.NodeModel
import com.yandex.dagger3.core.NonAliasBinding
import com.yandex.dagger3.core.ProvisionBinding
import com.yandex.dagger3.core.isScoped
import com.yandex.dagger3.core.resolveNonAliasBinding
import com.yandex.dagger3.generator.poetry.ExpressionBuilder
import com.yandex.dagger3.generator.poetry.TypeSpecBuilder
import com.yandex.dagger3.generator.poetry.buildExpression
import javax.inject.Provider

internal class ProvisionGenerator(
    graph: BindingGraph,
    fieldsNs: Namespace,
    methodsNs: Namespace,
    multiFactory: Provider<SlotSwitchingGenerator>,
    unscopedProviderGenerator: Provider<UnscopedProviderGenerator>,
    scopedProviderGenerator: Provider<ScopedProviderGenerator>,
    private val componentFactoryGenerator: Provider<ComponentFactoryGenerator>,
) : ComponentGenerator.Contributor {
    private val strategies: Map<NodeModel, ProvisionStrategy> = graph.localBindings.entries.associateBy(
        keySelector = { (binding, _) -> binding.target },
        valueTransform = { (maybeAlias, usage) ->
            // MAYBE: can there be any inconsistency with target due to alias resolve?
            val binding = graph.resolveNonAliasBinding(maybeAlias)
            if (binding.isScoped()) {
                if (usage.lazy + usage.provider == 0) {
                    // No need to generate actual provider instance, inline caching would do.
                    if (usage.direct == 1) InlineCreationStrategy(
                        binding = binding,
                        provisionGenerator = this,
                    ) else InlineCachingStrategy(
                        binding = binding,
                        fieldsNs = fieldsNs,
                        methodsNs = methodsNs,
                        provisionGenerator = this,
                    )
                } else {
                    // Generate actual provider instance.
                    ScopedProviderStrategy(
                        binding = binding,
                        multiFactory = multiFactory.get(),
                        cachingProvider = scopedProviderGenerator.get(),
                        fieldsNs = fieldsNs,
                        methodsNs = methodsNs,
                    )
                }
            } else {
                if (usage.lazy + usage.provider == 0) {
                    // Inline instance creation does the trick.
                    InlineCreationStrategy(
                        binding = binding,
                        provisionGenerator = this,
                    )
                } else {
                    val slot = multiFactory.get().requestSlot(binding)
                    CompositeStrategy(
                        directStrategy = InlineCreationStrategy(
                            binding = binding,
                            provisionGenerator = this,
                        ),
                        lazyStrategy = if (usage.lazy > 0) OnTheFlyScopedProviderCreationStrategy(
                            cachingProvider = scopedProviderGenerator.get(),
                            multiFactory = multiFactory.get(),
                            slot = slot,
                        ) else null,
                        providerStrategy = if (usage.provider > 0) OnTheFlyUnscopedProviderCreationStrategy(
                            unscopedProviderGenerator = unscopedProviderGenerator.get(),
                            multiFactory = multiFactory.get(),
                            slot = slot,
                        ) else null,
                    )
                }
            }
        },
    )

    override fun generate(builder: TypeSpecBuilder) {
        strategies.values.forEach {
            it.generateInComponent(builder)
        }
    }

    fun generateAccess(builder: ExpressionBuilder, dependency: NodeModel.Dependency) {
        val (node, kind) = dependency
        checkNotNull(strategies[node]).generateAccess(builder, kind)
    }

    fun generateAccess(builder: ExpressionBuilder, binding: NonAliasBinding): Unit = with(builder) {
        when (binding) {
            is InstanceBinding -> {
                // TODO: support foreign component instead of |this|.
                +"this.${componentFactoryGenerator.get().fieldNameFor(binding)}"
            }
            is ProvisionBinding -> {
                // TODO: rework call and buildExpression mess here.
                call(binding.provider, binding.params.asSequence().map { dependency ->
                    buildExpression {
                        generateAccess(this, dependency)
                    }
                })
            }
        }.let { /*exhaustive*/ }
    }
}