package com.yandex.yatagan.instrumentation.spi

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.bindings.AssistedInjectFactoryBinding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyEntryPointBinding
import com.yandex.yatagan.core.graph.bindings.MapBinding
import com.yandex.yatagan.core.graph.bindings.MultiBinding
import com.yandex.yatagan.core.graph.bindings.ProvisionBinding

/**
 * TODO: Doc
 */
public interface InstrumentationPlugin {
    public fun shouldInstrument(graph: BindingGraph): Boolean {
        return true
    }

    public fun instrumentComponentCreation(
        graph: BindingGraph,
        delegate: InstrumentableAfter,
    ) {
    }

    public fun instrumentProvisionCreation(
        binding: ProvisionBinding,
        delegate: InstrumentableAround,
    ) {
    }

    public fun instrumentProvisionAccess(
        binding: ProvisionBinding,
        delegate: InstrumentableAround,
    ) {
    }

    public fun instrumentAssistedInjectInstanceCreation(
        binding: AssistedInjectFactoryBinding,
        delegate: InstrumentableAround,
    ) {
    }

    public fun instrumentMultiBindingCreation(
        binding: MultiBinding,
        delegate: InstrumentableAround,
    ) {
    }

    public fun instrumentMapBindingCreation(
        binding: MapBinding,
        delegate: InstrumentableAround,
    ) {
    }

    public fun instrumentComponentDependencyEntryPointAccess(
        binding: ComponentDependencyEntryPointBinding,
        delegate: InstrumentableAround,
    ) {
    }
}
