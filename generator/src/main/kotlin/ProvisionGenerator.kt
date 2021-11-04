package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.AlternativesBinding
import com.yandex.daggerlite.core.BaseBinding
import com.yandex.daggerlite.core.Binding
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.BindingUsage
import com.yandex.daggerlite.core.ComponentDependencyBinding
import com.yandex.daggerlite.core.ComponentDependencyEntryPointBinding
import com.yandex.daggerlite.core.ComponentInstanceBinding
import com.yandex.daggerlite.core.EmptyBinding
import com.yandex.daggerlite.core.InstanceBinding
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvisionBinding
import com.yandex.daggerlite.core.SubComponentFactoryBinding
import com.yandex.daggerlite.core.isNotEmpty
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import javax.inject.Provider

internal class ProvisionGenerator(
    private val thisGraph: BindingGraph,
    fieldsNs: Namespace,
    methodsNs: Namespace,
    multiFactory: Provider<SlotSwitchingGenerator>,
    unscopedProviderGenerator: Provider<UnscopedProviderGenerator>,
    scopedProviderGenerator: Provider<ScopedProviderGenerator>,
    private val generators: Generators,
) : ComponentGenerator.Contributor {

    private val strategies: Map<Binding, ProvisionStrategy> = thisGraph.localBindings.entries.associateBy(
        keySelector = { (binding, _) -> binding },
        valueTransform = fun(entry: Map.Entry<Binding, BindingUsage>): ProvisionStrategy {
            val (binding, usage) = entry
            if (binding is EmptyBinding) {
                return EmptyProvisionStrategy
            }
            val strategy = if (binding.scope != null) {
                if (usage.lazy + usage.provider == 0) {
                    // No need to generate actual provider instance, inline caching would do.
                    // TODO: There's a theoretical possibility to use inline strategy here if it can be proven,
                    //  that this binding has *a single direct usage as a dependency of a binding of the same scope*.
                    InlineCachingStrategy(
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
            return if (usage.optional > 0) {
                fun conditionalStrategy(kind: DependencyKind): ConditionalProvisionStrategy {
                    return ConditionalProvisionStrategy(
                        underlying = strategy,
                        binding = binding,
                        generator = this,
                        methodsNs = methodsNs,
                        generators = generators,
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
        usage: BindingUsage,
        methodsNs: Namespace,
    ): ProvisionStrategy {
        val inline = InlineCreationStrategy(
            generators = generators,
            binding = binding,
        )

        if (binding.dependencyCount == 0)
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
            provisionGenerator = this,
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

    fun generateAccess(builder: ExpressionBuilder, dependency: NodeModel.Dependency) {
        val (node, kind) = dependency
        val binding = thisGraph.resolveBinding(node)
        generateAccess(builder, binding, kind)
    }

    private fun generateAccess(builder: ExpressionBuilder, binding: BaseBinding, kind: DependencyKind) {
        val generator = if (binding.owner != thisGraph) {
            // Inherited binding
            generators[binding.owner].generator
        } else this
        // Generate
        checkNotNull(generator.strategies[binding]).generateAccess(builder, kind, inside = thisGraph)
    }

    fun componentForBinding(inside: BindingGraph, owner: BindingGraph): String {
        return if (inside != owner) {
            "this." + generators[inside].factoryGenerator.fieldNameFor(owner)
        } else "this"
    }

    fun generateCreation(builder: ExpressionBuilder, binding: Binding): Unit = with(builder) {
        when (binding) {
            is InstanceBinding -> {
                val component = componentForBinding(inside = thisGraph, owner = binding.owner)
                val factory = generators[binding.owner].factoryGenerator
                +"$component.${factory.fieldNameFor(binding.input)}"
            }
            is ProvisionBinding -> {
                generateCall(
                    function = binding.provider,
                    arguments = binding.params,
                    instance = binding.requiredModuleInstance?.let { module ->
                        val component = componentForBinding(inside = thisGraph, owner = binding.owner)
                        "$component.${generators[binding.owner].factoryGenerator.fieldNameFor(module)}"
                    },
                ) { dependency -> generateAccess(this, dependency) }
            }
            is SubComponentFactoryBinding -> {
                +"new %T(".formatCode(generators[binding.targetGraph].factoryGenerator.implName)
                join(binding.targetGraph.usedParents) { parentGraph ->
                    +buildExpression {
                        +componentForBinding(inside = thisGraph, owner = parentGraph)
                    }
                }
                +")"
            }
            is AlternativesBinding -> {
                // The whole result is nullable if not exhaustive.
                with(builder) {
                    var exhaustive = false
                    binding.alternatives.forEach { alternative: NodeModel ->
                        val altBinding = thisGraph.resolveBinding(alternative)
                        if (altBinding.conditionScope.isNotEmpty) {
                            val expression = buildExpression {
                                val gen = generators[thisGraph].conditionGenerator
                                gen.expression(this, altBinding.conditionScope)
                            }
                            +"%L ? ".formatCode(expression)
                            generators[altBinding.owner].generator
                                .generateAccess(builder, altBinding, DependencyKind.Direct)
                            +" : "
                        } else {
                            generators[altBinding.owner].generator
                                .generateAccess(builder, altBinding, DependencyKind.Direct)
                            exhaustive = true
                        }
                    }
                    if (!exhaustive) {
                        +"null /*empty*/"
                    }
                }
            }
            is ComponentInstanceBinding -> {
                +componentForBinding(inside = thisGraph, owner = binding.owner)
            }
            is ComponentDependencyBinding -> {
                val factory = generators[binding.owner].factoryGenerator
                +factory.fieldNameFor(binding.input)
            }
            is ComponentDependencyEntryPointBinding -> {
                val factory = generators[binding.owner].factoryGenerator
                +factory.fieldNameFor(binding.input)
                +"."
                +binding.getter.name
                +"()"
            }
            is EmptyBinding -> throw AssertionError("not handled here")
        }.let { /*exhaustive*/ }
    }

    companion object {
        private val Binding.dependencyCount
            get() = when (this) {
                // TODO: sync this with core.
                is ProvisionBinding -> this.params.size
                is AlternativesBinding -> this.alternatives.size
                else -> 0
            }
    }
}