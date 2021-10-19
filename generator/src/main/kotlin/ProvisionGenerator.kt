package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.BaseBinding
import com.yandex.daggerlite.core.Binding
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.ComponentInstanceBinding
import com.yandex.daggerlite.core.InstanceBinding
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvisionBinding
import com.yandex.daggerlite.core.SubComponentFactoryBinding
import com.yandex.daggerlite.core.isScoped
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import javax.inject.Provider

internal class ProvisionGenerator(
    private val graph: BindingGraph,
    fieldsNs: Namespace,
    methodsNs: Namespace,
    multiFactory: Provider<SlotSwitchingGenerator>,
    unscopedProviderGenerator: Provider<UnscopedProviderGenerator>,
    scopedProviderGenerator: Provider<ScopedProviderGenerator>,
    private val generator: Generator,
) : ComponentGenerator.Contributor {
    private val strategies: Map<BaseBinding, ProvisionStrategy> = graph.localBindings.entries.associateBy(
        keySelector = { (binding, _) -> binding },
        valueTransform = { (binding, usage) ->
            if (binding.isScoped()) {
                if (usage.lazy + usage.provider == 0) {
                    // No need to generate actual provider instance, inline caching would do.
                    if (usage.direct == 1) InlineCreationStrategy(
                        binding = binding,
                        generator = generator,
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
                        provisionGenerator = this,
                        fieldsNs = fieldsNs,
                        methodsNs = methodsNs,
                    )
                }
            } else {
                if (usage.lazy + usage.provider == 0) {
                    // Inline instance creation does the trick.
                    InlineCreationStrategy(
                        binding = binding,
                        generator = generator,
                    )
                } else {
                    val slot = multiFactory.get().requestSlot(binding)
                    CompositeStrategy(
                        directStrategy = InlineCreationStrategy(
                            binding = binding,
                            generator = generator,
                        ),
                        lazyStrategy = if (usage.lazy > 0) OnTheFlyScopedProviderCreationStrategy(
                            cachingProvider = scopedProviderGenerator.get(),
                            binding = binding,
                            generator = this,
                            slot = slot,
                        ) else null,
                        providerStrategy = if (usage.provider > 0) OnTheFlyUnscopedProviderCreationStrategy(
                            unscopedProviderGenerator = unscopedProviderGenerator.get(),
                            binding = binding,
                            generator = this,
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
        val binding = graph.resolveBinding(node)
        val generator = if (binding.owner != graph) {
            // Inherited binding
            generator.forComponent(binding.owner).generator
        } else this
        // Generate
        checkNotNull(generator.strategies[binding]).generateAccess(builder, kind, inside = graph)
    }

    fun componentForBinding(binding: Binding, target: BindingGraph): String {
        return if (binding.owner != target) {
            "this." + generator.forComponent(target).factoryGenerator.fieldNameFor(binding.owner)
        } else "this"
    }

    fun generateAccess(builder: ExpressionBuilder, binding: Binding): Unit = with(builder) {
        when (binding) {
            is InstanceBinding -> {
                val component = componentForBinding(binding, target = graph)
                val factory = generator.forComponent(binding.owner).factoryGenerator
                +"$component.${factory.fieldNameFor(binding)}"
            }
            is ProvisionBinding -> {
                // TODO: rework call and buildExpression mess here.
                call(binding.provider, binding.params.asSequence().map { dependency ->
                    buildExpression {
                        generateAccess(this, dependency)
                    }
                })
            }
            is SubComponentFactoryBinding -> {
                val factoryGenerator = generator.forComponent(binding.target.createdComponent).factoryGenerator
                +"new %T(".formatCode(factoryGenerator.implName)
                join(binding.target.createdComponent.graph.usedParents.asSequence()) { parentGraph ->
                    +buildExpression {
                        generator.forComponent(parentGraph).generator
                            .generateAccess(this, NodeModel.Dependency(parentGraph.component))
                    }
                }
                +")"
            }
            is ComponentInstanceBinding -> {
                +componentForBinding(binding, target = graph)
            }
        }.let { /*exhaustive*/ }
    }
}