package com.yandex.daggerlite.generator

import com.yandex.daggerlite.core.Binding
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.ProvisionBinding
import com.yandex.daggerlite.generator.poetry.ExpressionBuilder
import com.yandex.daggerlite.generator.poetry.TypeSpecBuilder
import com.yandex.daggerlite.generator.poetry.buildExpression
import javax.lang.model.element.Modifier.PRIVATE

internal class InlineCachingStrategy(
    private val binding: ProvisionBinding,
    private val provisionGenerator: ProvisionGenerator,
    fieldsNs: Namespace,
    methodsNs: Namespace,
) : ProvisionStrategy {
    private val instanceFieldName: String
    private val instanceAccessorName: String

    init {
        val name = binding.target.name
        instanceFieldName = fieldsNs.name(name, "Instance")
        instanceAccessorName = methodsNs.name(name)
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
                    provisionGenerator.generateAccess(this, binding)
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
                    val component = provisionGenerator.componentForBinding(binding, inside)
                    +"$component.%N()".formatCode(instanceAccessorName)
                }
                DependencyKind.Lazy, DependencyKind.Provider -> throw AssertionError()
            }.let { /*exhaustive*/ }
        }
    }
}

internal class ScopedProviderStrategy(
    multiFactory: SlotSwitchingGenerator,
    private val cachingProvider: ScopedProviderGenerator,
    private val binding: ProvisionBinding,
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
            val component = provisionGenerator.componentForBinding(binding, inside)
            when (kind) {
                DependencyKind.Direct -> +"$component.$instanceAccessorName()"
                DependencyKind.Lazy, DependencyKind.Provider -> +"$component.$providerAccessorName()"
            }.let { /*exhaustive*/ }
        }
    }
}

internal class InlineCreationStrategy(
    private val provisionGenerator: ProvisionGenerator,
    private val binding: Binding,
) : ProvisionStrategy {
    init {
        println("foo")
    }

    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        assert(kind == DependencyKind.Direct)
        provisionGenerator.generateAccess(builder, binding, inside)
    }
}

internal class CompositeStrategy(
    private val directStrategy: ProvisionStrategy,
    private val lazyStrategy: ProvisionStrategy?,
    private val providerStrategy: ProvisionStrategy?,
) : ProvisionStrategy {
    override fun generateInComponent(builder: TypeSpecBuilder) {
        directStrategy.generateInComponent(builder)
        lazyStrategy?.generateInComponent(builder)
        providerStrategy?.generateInComponent(builder)
    }

    override fun generateAccess(builder: ExpressionBuilder, kind: DependencyKind, inside: BindingGraph) {
        when (kind) {
            DependencyKind.Direct -> directStrategy.generateAccess(builder, kind, inside)
            DependencyKind.Lazy -> checkNotNull(lazyStrategy).generateAccess(builder, kind, inside)
            DependencyKind.Provider -> checkNotNull(providerStrategy).generateAccess(builder, kind, inside)
        }
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
            +"new %T(${generator.componentForBinding(binding, inside)}, $slot)".formatCode(cachingProvider.name)
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
            +"new %T(${generator.componentForBinding(binding, inside)}, $slot)"
                .formatCode(unscopedProviderGenerator.name)
        }
    }
}