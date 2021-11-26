package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.BootstrapInterfaceModel
import com.yandex.daggerlite.core.ComponentDependencyInput
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.InstanceInput
import com.yandex.daggerlite.core.ModuleInstanceInput
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.allInputs
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.graph.BaseBinding
import com.yandex.daggerlite.graph.BindingGraph

/**
 * @param graph incomplete graph. All required fields must be initialized.
 */
internal fun buildBindingsSequence(
    graph: BindingGraph,
    langModelFactory: LangModelFactory,
): Sequence<BaseBinding> = sequence {
    // Gather bindings from modules
    val seenSubcomponents = hashSetOf<ComponentModel>()
    val bootstrapSets = HashMap<BootstrapInterfaceModel, MutableSet<NodeModel>>()
    val multiBindings = linkedMapOf<NodeModel, MutableSet<NodeModel>>()
    for (module: ModuleModel in graph.modules) {
        // All bindings from installed modules
        for (bindingModel in module.bindings) {
            val binding = when (bindingModel) {
                is BindsBindingModel -> when (bindingModel.sources.count()) {
                    0 -> ModuleHostedEmptyBindingImpl(
                        owner = graph,
                        impl = bindingModel,
                    )
                    1 -> AliasBindingImpl(
                        owner = graph,
                        impl = bindingModel,
                    )
                    else -> AlternativesBindingImpl(
                        owner = graph,
                        impl = bindingModel,
                    )
                }
                is ProvidesBindingModel -> {
                    bindingModel.conditionScopeFor(graph.variant)?.let { conditionScope ->
                        ProvisionBindingImpl(
                            impl = bindingModel,
                            owner = graph,
                            conditionScope = conditionScope,
                        )
                    } ?: ModuleHostedEmptyBindingImpl(
                        owner = graph,
                        impl = bindingModel,
                    )
                }
            }
            yield(binding)
            // Handle multi-bindings
            if (bindingModel.isMultibinding) {
                multiBindings.getOrPut(bindingModel.target, ::mutableSetOf) += binding.target
            }
        }
        // Subcomponent factories (distinct).
        for (subcomponent: ComponentModel in module.subcomponents) {
            if (seenSubcomponents.add(subcomponent)) {
                // MAYBE: Factory is actually required.
                subcomponent.factory?.let { factory: ComponentFactoryModel ->
                    factory.createdComponent.conditionScopeFor(graph.variant)?.let { conditionScope ->
                        yield(SubComponentFactoryBindingImpl(
                            owner = graph,
                            factory = factory,
                            conditionScope = conditionScope,
                        ))
                    } ?: yield(ImplicitEmptyBindingImpl(
                        owner = graph,
                        target = factory.asNode()
                    ))
                }
            }
        }
        // Handle bootstrap lists
        for (declared: BootstrapInterfaceModel in module.declaredBootstrapInterfaces) {
            bootstrapSets.getOrPut(declared, ::linkedSetOf)
        }
        for (nodeModel: NodeModel in module.bootstrap) {
            for (bootstrapInterface: BootstrapInterfaceModel in nodeModel.bootstrapInterfaces) {
                bootstrapSets.getOrPut(bootstrapInterface, ::linkedSetOf) += nodeModel
            }
        }
    }
    // Gather bindings from factory
    graph.model.factory?.let { factory: ComponentFactoryModel ->
        for (input: ComponentFactoryModel.Input in factory.allInputs) when (input) {
            is ComponentDependencyInput -> {
                // Binding for the dependency component itself.
                yield(ComponentDependencyBindingImpl(input = input, owner = graph))
                // Bindings backed by the component entry-points.
                for (entryPoint: ComponentModel.EntryPoint in input.component.entryPoints)
                    if (entryPoint.dependency.kind == DependencyKind.Direct)
                        yield(ComponentDependencyEntryPointBindingImpl(
                            owner = graph,
                            entryPoint = entryPoint,
                            input = input,
                        ))
            }
            // Instance binding
            is InstanceInput -> yield(InstanceBindingImpl(input = input, owner = graph))
            is ModuleInstanceInput -> {/*no binding for module*/
            }
        }.let { /*exhaustive*/ }
    }
    // Bootstrap lists
    for ((bootstrap: BootstrapInterfaceModel, nodes: Set<NodeModel>) in bootstrapSets) {
        yield(BootstrapListBindingImpl(
            owner = graph,
            target = bootstrap.asNode(langModelFactory),
            inputs = nodes,
        ))
    }

    // Multi-bindings
    for ((target: NodeModel, contributions: Set<NodeModel>) in multiBindings) {
        yield(MultiBindingImpl(
            owner = graph,
            target = target.multiBoundListNode(langModelFactory),
            contributions = contributions,
        ))
    }

    // This component binding
    yield(ComponentInstanceBindingImpl(graph = graph))
}