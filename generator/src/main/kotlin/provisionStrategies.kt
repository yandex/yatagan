package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.isOptional
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import javax.lang.model.element.Modifier.PRIVATE

internal class InlineCachingStrategy(
    private val binding: Binding,
    fieldsNs: Namespace,
    methodsNs: Namespace,
) : AccessStrategy {
    private val instanceFieldName: String
    private val instanceAccessorName: String

    init {
        val target = binding.target
        instanceFieldName = fieldsNs.name(target.name, suffix = "instance", qualifier = target.qualifier)
        instanceAccessorName = methodsNs.name(target.name, prefix = "cache", qualifier = target.qualifier)
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
                    binding.generateCreation(builder = this, inside = binding.owner)
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
                    val component = componentForBinding(inside = inside, binding = binding)
                    +"$component.%N()".formatCode(instanceAccessorName)
                }
                else -> throw AssertionError()
            }.let { /*exhaustive*/ }
        }
    }
}

internal class ScopedProviderStrategy(
    private val binding: Binding,
    multiFactory: SlotSwitchingGenerator,
    cachingProvider: ScopedProviderGenerator,
    fieldsNs: Namespace,
    methodsNs: Namespace,
) : AccessStrategy {
    private val providerName = cachingProvider.name
    private val slot = multiFactory.requestSlot(binding)
    private val providerFieldName: String
    private val providerAccessorName: String
    private val instanceAccessorName: String

    init {
        val target = binding.target
        providerFieldName = fieldsNs.name(target.name, suffix = "provider", qualifier = target.qualifier)
        providerAccessorName = methodsNs.name(target.name, prefix = "scopedProviderFor", qualifier = target.qualifier)
        // TODO: Here we assume binding has `Direct` requests, which may not be true - unneeded code is generated.
        instanceAccessorName = methodsNs.name(target.name, prefix = "unwrapProvider", qualifier = target.qualifier)
    }

    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
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
            val component = componentForBinding(inside = inside, binding = binding)
            when (kind) {
                DependencyKind.Direct -> +"$component.$instanceAccessorName()"
                DependencyKind.Lazy, DependencyKind.Provider -> +"$component.$providerAccessorName()"
                else -> throw AssertionError()
            }.let { /*exhaustive*/ }
        }
    }
}

internal class InlineCreationStrategy(
    private val binding: Binding,
) : AccessStrategy {
    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        assert(kind == DependencyKind.Direct)
        binding.generateCreation(builder = builder, inside = inside)
    }
}

internal class WrappingAccessorStrategy(
    private val binding: Binding,
    private val underlying: AccessStrategy,
    methodsNs: Namespace,
) : AccessStrategy {
    private val accessorName: String

    init {
        val target = binding.target
        accessorName = methodsNs.name(target.name, prefix = "access", qualifier = target.qualifier)
    }

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
        val component = componentForBinding(inside = inside, binding = binding)
        builder.apply {
            +"$component.$accessorName()"
        }
    }
}

internal class CompositeStrategy(
    private val directStrategy: AccessStrategy,
    private val lazyStrategy: AccessStrategy? = directStrategy,
    private val providerStrategy: AccessStrategy? = directStrategy,
    private val conditionalStrategy: AccessStrategy? = null,
    private val conditionalLazyStrategy: AccessStrategy? = null,
    private val conditionalProviderStrategy: AccessStrategy? = null,
) : AccessStrategy {
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
    cachingProvider: ScopedProviderGenerator,
    private val binding: Binding,
    private val slot: Int,
) : AccessStrategy {
    private val providerName = cachingProvider.name

    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        require(kind == DependencyKind.Lazy)
        builder.apply {
            val component = componentForBinding(inside = inside, binding = binding)
            +"new %T($component, $slot)".formatCode(providerName)
        }
    }
}

internal class OnTheFlyUnscopedProviderCreationStrategy(
    unscopedProviderGenerator: UnscopedProviderGenerator,
    private val binding: Binding,
    private val slot: Int,
) : AccessStrategy {
    private val providerName = unscopedProviderGenerator.name

    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        require(kind == DependencyKind.Provider)
        builder.apply {
            val component = componentForBinding(inside = inside, binding = binding)
            +"new %T($component, $slot)".formatCode(providerName)
        }
    }
}

internal class ConditionalAccessStrategy(
    private val underlying: AccessStrategy,
    private val binding: Binding,
    methodsNs: Namespace,
    private val dependencyKind: DependencyKind,
) : AccessStrategy {
    private val accessorName: String

    init {
        val target = binding.target
        accessorName = methodsNs.name(target.name, prefix = "optOf", qualifier = target.qualifier)
    }

    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
        method(accessorName) {
            modifiers(PRIVATE)
            returnType(Names.Optional)
            when {
                !binding.conditionScope.isAlways -> {
                    val expression = buildExpression {
                        val gen = Generators[binding.owner].conditionGenerator
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
        val component = componentForBinding(inside = inside, binding = binding)
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

object EmptyAccessStrategy : AccessStrategy {
    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        assert(kind.isOptional)
        with(builder) {
            +"%T.empty()".formatCode(Names.Optional)
        }
    }
}