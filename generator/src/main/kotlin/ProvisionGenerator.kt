package com.yandex.dagger3.generator

import com.yandex.dagger3.core.Binding
import com.yandex.dagger3.core.BindingGraph
import com.yandex.dagger3.core.ComponentInstanceBinding
import com.yandex.dagger3.core.ComponentModel
import com.yandex.dagger3.core.InstanceBinding
import com.yandex.dagger3.core.NodeModel
import com.yandex.dagger3.core.NonAliasBinding
import com.yandex.dagger3.core.ProvisionBinding
import com.yandex.dagger3.core.SubComponentFactoryBinding
import com.yandex.dagger3.core.isScoped
import com.yandex.dagger3.core.resolveNonAliasBinding
import com.yandex.dagger3.generator.poetry.ExpressionBuilder
import com.yandex.dagger3.generator.poetry.TypeSpecBuilder
import com.yandex.dagger3.generator.poetry.buildExpression
import javax.inject.Provider

internal class ProvisionGenerator(
    private val graph: BindingGraph,
    fieldsNs: Namespace,
    methodsNs: Namespace,
    multiFactory: Provider<SlotSwitchingGenerator>,
    unscopedProviderGenerator: Provider<UnscopedProviderGenerator>,
    scopedProviderGenerator: Provider<ScopedProviderGenerator>,
    private val componentFactoryGenerator: Provider<ComponentFactoryGenerator>,
    private val generators: Map<ComponentModel, ComponentGenerator>,
) : ComponentGenerator.Contributor {
    private val strategies: Map<Binding, ProvisionStrategy> = graph.localBindings.entries.associateBy(
        keySelector = { (binding, _) -> binding },
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
        val binding = graph.resolveBinding(node)
        val generator = if (binding.owner != graph) {
            // Inherited binding
            checkNotNull(generators[binding.owner.component])
                .provisionGenerator.get()
        } else this
        // Generate
        checkNotNull(generator.strategies[binding]).generateAccess(builder, kind, inside = graph)
    }

    fun componentForBinding(binding: NonAliasBinding, target: BindingGraph): String {
        return if (binding.owner != target) {
            val factoryGenerator = checkNotNull(generators[target.component]).componentFactoryGenerator.get()
            "this." + factoryGenerator.fieldNameFor(binding.owner)
        } else "this"
    }

    fun generateAccess(
        builder: ExpressionBuilder,
        binding: NonAliasBinding,
        inside: BindingGraph = graph,
    ): Unit = with(builder) {
        when (binding) {
            is InstanceBinding -> {
                val component = componentForBinding(binding, target = inside)
                +"$component.${componentFactoryGenerator.get().fieldNameFor(binding)}"
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
                val childGenerator = checkNotNull(generators[binding.target.target])
                // fixme: subcomponent factory constructor accepts parent component(s)
                val generator = childGenerator.componentFactoryGenerator.get()
                +"new %T(".formatCode(generator.factoryImplName)
                join(binding.target.target.graph.usedParents.asSequence()) { graph ->
                    +buildExpression {
                        generateAccess(this, NodeModel.Dependency(graph.component))
                    }
                }
                +")"
            }
            is ComponentInstanceBinding -> {
                +componentForBinding(binding, target = inside)
            }
        }.let { /*exhaustive*/ }
    }
}