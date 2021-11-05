package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.Binding
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.isNotEmpty
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.Names
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import javax.lang.model.element.Modifier.PRIVATE

internal class InlineCachingStrategy(
    private val binding: Binding,
    private val provisionGenerator: ProvisionGenerator,
    fieldsNs: Namespace,
    methodsNs: Namespace,
) : ProvisionStrategy {
    private val instanceFieldName: String
    private val instanceAccessorName: String

    init {
        val name = binding.target.name
        // TODO: use qualifiers for name formation.
        instanceFieldName = fieldsNs.name(name, "Instance")
        instanceAccessorName = methodsNs.name("cache", name)
    }

    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
        val targetType = binding.target.typeName()
        field(targetType, instanceFieldName) { modifiers(PRIVATE) }
        method(instanceAccessorName) {
            modifiers(PRIVATE)
            returnType(targetType)
            +"%T local = this.%N".formatCode(targetType, instanceFieldName)
            controlFlow("if (local == null)") {
                +buildExpression {
                    +"local = "
                    provisionGenerator.generateCreation(this, binding)
                }
                +"this.%N = local".formatCode(instanceFieldName)
            }
            +"return local"
        }
    }

    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        builder.apply {
            when (kind) {
                DependencyKind.Direct -> {
                    val component = provisionGenerator.componentForBinding(inside = inside, owner = binding.owner)
                    +"$component.%N()".formatCode(instanceAccessorName)
                }
                else -> throw AssertionError()
            }.let { /*exhaustive*/ }
        }
    }
}

internal class ScopedProviderStrategy(
    multiFactory: SlotSwitchingGenerator,
    private val cachingProvider: ScopedProviderGenerator,
    private val binding: Binding,
    private val provisionGenerator: ProvisionGenerator,
    fieldsNs: Namespace,
    methodsNs: Namespace,
) : ProvisionStrategy {
    private val slot = multiFactory.requestSlot(binding)
    private val providerFieldName: String
    private val providerAccessorName: String
    private val instanceAccessorName: String

    init {
        val name = binding.target.name
        providerFieldName = fieldsNs.name(name, "Provider")
        providerAccessorName = methodsNs.name("providerOf", name)
        instanceAccessorName = methodsNs.name("instOf", name)
    }

    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
        val providerName = cachingProvider.name
        field(providerName, providerFieldName) { modifiers(PRIVATE) }
        method(providerAccessorName) {
            modifiers(PRIVATE)
            returnType(providerName)
            +"%T local = this.$providerFieldName".formatCode(providerName)
            controlFlow("if (local == null)") {
                +"local = new %T(this, $slot)".formatCode(providerName)
                +"this.$providerFieldName = local"
            }
            +"return local"
        }
        method(instanceAccessorName) {
            modifiers(PRIVATE)
            val typeName = binding.target.typeName()
            returnType(typeName)
            +"return (%T) $providerAccessorName().get()".formatCode(typeName)
        }
    }

    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        // Generate access either to provider, lazy or direct.
        builder.apply {
            val component = provisionGenerator.componentForBinding(inside = inside, owner = binding.owner)
            when (kind) {
                DependencyKind.Direct -> +"$component.$instanceAccessorName()"
                DependencyKind.Lazy, DependencyKind.Provider -> +"$component.$providerAccessorName()"
                else -> throw AssertionError()
            }.let { /*exhaustive*/ }
        }
    }
}

internal class InlineCreationStrategy(
    private val generators: Generators,
    private val binding: Binding,
) : ProvisionStrategy {
    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        assert(kind == DependencyKind.Direct)
        generators[inside].generator.generateCreation(builder, binding)
    }
}

internal class WrappingAccessorStrategy(
    private val provisionGenerator: ProvisionGenerator,
    private val binding: Binding,
    private val underlying: ProvisionStrategy,
    methodsNs: Namespace,
) : ProvisionStrategy {
    private val accessorName = methodsNs.name("create", binding.target.name)

    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
        method(accessorName) {
            modifiers(PRIVATE)
            returnType(binding.target.typeName())
            +buildExpression {
                +"return "
                underlying.generateAccess(builder = this, kind = DependencyKind.Direct, inside = binding.owner)
            }
        }
    }

    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        assert(kind == DependencyKind.Direct)
        val component = provisionGenerator.componentForBinding(inside = inside, owner = binding.owner)
        builder.apply {
            +"$component.$accessorName()"
        }
    }
}

internal class CompositeStrategy(
    private val directStrategy: ProvisionStrategy,
    private val lazyStrategy: ProvisionStrategy? = directStrategy,
    private val providerStrategy: ProvisionStrategy? = directStrategy,
    private val conditionalStrategy: ProvisionStrategy? = null,
    private val conditionalLazyStrategy: ProvisionStrategy? = null,
    private val conditionalProviderStrategy: ProvisionStrategy? = null,
) : ProvisionStrategy {
    override fun generateInComponent(builder: TypeSpecBuilder) {
        setOfNotNull(
            directStrategy,
            lazyStrategy,
            providerStrategy,
            conditionalStrategy,
            conditionalLazyStrategy,
            conditionalProviderStrategy,
        ).forEach { it.generateInComponent(builder) }
    }

    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        when (kind) {
            DependencyKind.Direct -> directStrategy.generateAccess(builder, kind, inside)
            DependencyKind.Lazy -> checkNotNull(lazyStrategy).generateAccess(builder, kind, inside)
            DependencyKind.Provider -> checkNotNull(providerStrategy).generateAccess(builder, kind, inside)
            DependencyKind.Optional -> checkNotNull(conditionalStrategy).generateAccess(builder, kind, inside)
            DependencyKind.OptionalLazy -> checkNotNull(conditionalLazyStrategy).generateAccess(builder, kind, inside)
            DependencyKind.OptionalProvider -> checkNotNull(conditionalProviderStrategy).generateAccess(builder, kind, inside)
        }.let { /*exhaustive*/ }
    }
}

internal class OnTheFlyScopedProviderCreationStrategy(
    private val cachingProvider: ScopedProviderGenerator,
    private val generator: ProvisionGenerator,
    private val binding: Binding,
    private val slot: Int,
) : ProvisionStrategy {

    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        require(kind == DependencyKind.Lazy)
        builder.apply {
            val component = generator.componentForBinding(inside = inside, owner = binding.owner)
            +"new %T($component, $slot)".formatCode(cachingProvider.name)
        }
    }
}

internal class OnTheFlyUnscopedProviderCreationStrategy(
    private val unscopedProviderGenerator: UnscopedProviderGenerator,
    private val generator: ProvisionGenerator,
    private val binding: Binding,
    private val slot: Int,
) : ProvisionStrategy {

    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        require(kind == DependencyKind.Provider)
        builder.apply {
            val component = generator.componentForBinding(inside = inside, owner = binding.owner)
            +"new %T($component, $slot)".formatCode(unscopedProviderGenerator.name)
        }
    }
}

internal class ConditionalProvisionStrategy(
    private val underlying: ProvisionStrategy,
    private val binding: Binding,
    private val generator: ProvisionGenerator,
    private val generators: Generators,
    methodsNs: Namespace,
    private val dependencyKind: DependencyKind,
) : ProvisionStrategy {
    private val accessorName = methodsNs.name("optionalOf", binding.target.name)

    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
        method(accessorName) {
            modifiers(PRIVATE)
            returnType(Names.Optional)
            when {
                binding.conditionScope.isNotEmpty -> {
                    val expression = buildExpression {
                        val gen = generators[binding.owner].conditionGenerator
                        gen.expression(this, binding.conditionScope)
                    }
                    +buildExpression {
                        +"return %L ? %T.of(".formatCode(expression, Names.Optional)
                        underlying.generateAccess(this, dependencyKind, binding.owner)
                        +") : %T.empty()".formatCode(Names.Optional)
                    }
                }
                else -> +buildExpression {
                    +"return %T.of(".formatCode(Names.Optional)
                    underlying.generateAccess(this, dependencyKind, binding.owner)
                    +")"
                }
            }
        }
    }

    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        check(dependencyKind.asOptional() == kind)
        val component = generator.componentForBinding(inside = inside, owner = binding.owner)
        builder.apply {
            +"$component.$accessorName()"
        }
    }
}

private fun DependencyKind.asOptional(): DependencyKind = when (this) {
    DependencyKind.Direct -> DependencyKind.Optional
    DependencyKind.Lazy -> DependencyKind.OptionalLazy
    DependencyKind.Provider -> DependencyKind.OptionalProvider
    else -> this
}

object EmptyProvisionStrategy : ProvisionStrategy {
    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        assert(kind.asOptional() == kind) { "Trying to use Empty strategy in non-optional context" }
        with(builder) {
            +"%T.empty()".formatCode(Names.Optional)
        }
    }
}