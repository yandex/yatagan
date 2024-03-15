package com.yandex.yatagan.instrumentation.impl

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AlternativesBinding
import com.yandex.yatagan.core.graph.bindings.AssistedInjectFactoryBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyBinding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyEntryPointBinding
import com.yandex.yatagan.core.graph.bindings.ComponentInstanceBinding
import com.yandex.yatagan.core.graph.bindings.ConditionExpressionValueBinding
import com.yandex.yatagan.core.graph.bindings.EmptyBinding
import com.yandex.yatagan.core.graph.bindings.InstanceBinding
import com.yandex.yatagan.core.graph.bindings.MapBinding
import com.yandex.yatagan.core.graph.bindings.MultiBinding
import com.yandex.yatagan.core.graph.bindings.ProvisionBinding
import com.yandex.yatagan.core.graph.bindings.SubComponentBinding
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.instrumentation.Statement
import com.yandex.yatagan.instrumentation.spi.InstrumentableAfter
import com.yandex.yatagan.instrumentation.spi.InstrumentableAround
import com.yandex.yatagan.instrumentation.spi.InstrumentationPlugin
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class Instrumentation(
    private val plugin: InstrumentationPlugin,
) {
    private val _newDependencies: MutableSet<NodeDependency> = mutableSetOf()
    private val instrumentor = BindingInstrumentor()

    val newDependencies: Set<NodeDependency>
        get() = _newDependencies

    fun instrument(graph: BindingGraph) {
        val instrumented = object : InstrumentedAfter, InstrumentableAfter {
            override val after = arrayListOf<Statement>()
        }
        plugin.instrumentComponentCreation(
            graph = graph,
            delegate = instrumented,
        )
        instrumented.collectNewDependencies(_newDependencies)
        graph.instrumentedCreation = instrumented
    }

    fun instrument(binding: Binding) {
        binding.accept(instrumentor)
    }

    private inner class BindingInstrumentor : Binding.Visitor<Unit> {
        private var around = InstrumentingAround()

        override fun visitProvision(binding: ProvisionBinding) {
            // 1. Instrument access
            withInstrumentedAround { access ->
                plugin.instrumentProvisionAccess(binding, access)
            }?.also(::collectNewDependencies)?.let { binding.instrumentedAccess = it }

            // 2. Instrument creation
            withInstrumentedAround { creation ->
                plugin.instrumentProvisionCreation(binding, creation)
            }?.also(::collectNewDependencies)?.let { binding.instrumentedCreation = it }
        }

        override fun visitAssistedInjectFactory(binding: AssistedInjectFactoryBinding) {
            withInstrumentedAround { creation ->
                plugin.instrumentAssistedInjectInstanceCreation(binding, creation)
            }?.also(::collectNewDependencies)?.let { binding.instrumentedCreation = it }
        }

        override fun visitComponentDependencyEntryPoint(binding: ComponentDependencyEntryPointBinding) {
            withInstrumentedAround { access ->
                plugin.instrumentComponentDependencyEntryPointAccess(binding, access)
            }?.also(::collectNewDependencies)?.let { binding.instrumentedAccess = it }
        }

        override fun visitMulti(binding: MultiBinding) {
            withInstrumentedAround { creation ->
                plugin.instrumentMultiBindingCreation(binding, creation)
            }?.also(::collectNewDependencies)?.let { binding.instrumentedCreation = it }
        }

        override fun visitMap(binding: MapBinding) {
            withInstrumentedAround { creation ->
                plugin.instrumentMapBindingCreation(binding, creation)
            }?.also(::collectNewDependencies)?.let { binding.instrumentedCreation = it }
        }

        override fun visitInstance(binding: InstanceBinding) = Unit
        override fun visitAlternatives(binding: AlternativesBinding) = Unit
        override fun visitSubComponent(binding: SubComponentBinding) = Unit
        override fun visitComponentDependency(binding: ComponentDependencyBinding) = Unit
        override fun visitComponentInstance(binding: ComponentInstanceBinding) = Unit
        override fun visitConditionExpressionValue(binding: ConditionExpressionValueBinding) = Unit
        override fun visitEmpty(binding: EmptyBinding) = Unit

        override fun visitOther(binding: Binding) = throw AssertionError()

        private inline fun withInstrumentedAround(block: (InstrumentingAround) -> Unit): InstrumentedAround? {
            contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
            block(around)
            return if (!around.isEmpty()) {
                // Was used, replace it
                around.also { around = InstrumentingAround() }
            } else null
        }

        private fun collectNewDependencies(instrumented: InstrumentedAround) {
            instrumented.collectNewDependencies(_newDependencies)
        }
    }

    private class InstrumentingAround : InstrumentedAround, InstrumentableAround {
        override val before: MutableList<Statement> = arrayListOf()
        override val after: MutableList<Statement> = arrayListOf()
    }
}
