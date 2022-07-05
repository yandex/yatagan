package com.yandex.daggerlite.generator

import com.squareup.javapoet.ClassName
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.isOptional
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.VOLATILE

internal abstract class CachingStrategyBase(
    protected val binding: Binding,
    fieldsNs: Namespace,
    methodsNs: Namespace,
) : AccessStrategy {
    protected val instanceFieldName: String
    protected val instanceAccessorName: String

    init {
        val target = binding.target
        instanceFieldName = fieldsNs.name(target.name, suffix = "instance", qualifier = target.qualifier)
        instanceAccessorName = methodsNs.name(target.name, prefix = "cache", qualifier = target.qualifier)
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

internal class CachingStrategySingleThread(
    binding: Binding,
    fieldsNs: Namespace,
    methodsNs: Namespace,
) : CachingStrategyBase(binding, fieldsNs, methodsNs) {
    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
        val targetType = binding.target.typeName()
        field(ClassName.OBJECT, instanceFieldName) {
            modifiers(PRIVATE)  // PRIVATE: only accessed via its accessor.
        }
        method(instanceAccessorName) {
            modifiers(/*package-private*/)
            returnType(targetType)
            +"%T local = this.%N".formatCode(ClassName.OBJECT, instanceFieldName)
            controlFlow("if (local == null)") {
                +"%T.assertThreadAccess()".formatCode(Names.ThreadAssertions)
                +buildExpression {
                    +"local = "
                    binding.generateCreation(builder = this, inside = binding.owner)
                }
                +"this.%N = local".formatCode(instanceFieldName)
            }
            +"return (%T) local".formatCode(targetType)
        }
    }
}

internal class CachingStrategyMultiThread(
    binding: Binding,
    fieldsNs: Namespace,
    methodsNs: Namespace,
    lock: LockGenerator,
) : CachingStrategyBase(binding, fieldsNs, methodsNs) {
    private val lockName = lock.name

    override fun generateInComponent(builder: TypeSpecBuilder) = with(builder) {
        val targetType = binding.target.typeName()
        field(ClassName.OBJECT, instanceFieldName) {
            modifiers(PRIVATE, VOLATILE)  // PRIVATE: only accessed via its accessor.
            initializer {
                +"new %T()".formatCode(lockName)
            }
        }
        method(instanceAccessorName) {
            modifiers(/*package-private*/)
            returnType(targetType)
            +"%T local = this.%N".formatCode(ClassName.OBJECT, instanceFieldName)
            controlFlow("if (local instanceof %T)".formatCode(lockName)) {
                controlFlow("synchronized (local)") {
                    +"local = this.%N".formatCode(instanceFieldName)
                    controlFlow("if (local instanceof %T)".formatCode(lockName)) {
                        +buildExpression {
                            +"local = "
                            binding.generateCreation(builder = this, inside = binding.owner)
                        }
                        +"this.%N = local".formatCode(instanceFieldName)
                    }
                }
            }
            +"return (%T) local".formatCode(targetType)
        }
    }
}

internal class SlotProviderStrategy(
    private val binding: Binding,
    multiFactory: SlotSwitchingGenerator,
    cachingProvider: UnscopedProviderGenerator,
) : AccessStrategy {

    private val providerName = cachingProvider.name
    private val slot = multiFactory.requestSlot(binding)

    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        when (kind) {
            DependencyKind.Lazy, DependencyKind.Provider -> builder.apply {
                val component = componentForBinding(inside = inside, binding = binding)
                // Provider class is chosen based on component (delegate) type - it will be the right one.
                +"new %T($component, $slot)".formatCode(providerName)
            }
            else -> throw AssertionError()
        }.let { /*exhaustive*/ }
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
            modifiers(/*package-private*/)
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
    private val providerStrategy: AccessStrategy? = lazyStrategy,
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
        require(kind == DependencyKind.Lazy || kind == DependencyKind.Provider)
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
            modifiers(/*package-private*/)
            returnType(Names.Optional)
            when {
                !binding.conditionScope.isAlways -> {
                    val expression = buildExpression {
                        binding.owner[ConditionGenerator]
                            .expression(builder = this, conditionScope = binding.conditionScope, inside = binding.owner)
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