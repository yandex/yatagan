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

internal fun buildBindingsSequence(
    graph: BindingGraph,
    modules: Collection<ModuleModel>,
    langModelFactory: LangModelFactory,
): Sequence<BaseBinding> = sequence {
    // Gather bindings from modules
    val seenSubcomponents = hashSetOf<ComponentModel>()
    val bootstrapSets = HashMap<BootstrapInterfaceModel, MutableSet<NodeModel>>()
    for (module: ModuleModel in modules) {
        // All bindings from installed modules
        for (bindingModel in module.bindings) {
            yield(when (bindingModel) {
                is BindsBindingModel -> when (bindingModel.sources.count()) {
                    0 -> EmptyBindingImpl(
                        owner = graph,
                        target = bindingModel.target,
                        originModule = module,
                        reason = "explicitly absent by empty @Binds directive"
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
                    } ?: EmptyBindingImpl(
                        owner = graph,
                        target = bindingModel.target,
                        originModule = module,
                        reason = "Ruled out by component variant"
                    )
                }
            })
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
                    } ?: yield(EmptyBindingImpl(
                        owner = graph,
                        target = factory.asNode(),
                        reason = "ruled out by component conditional filter"
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

    // This component binding
    yield(ComponentInstanceBindingImpl(graph = graph))
}