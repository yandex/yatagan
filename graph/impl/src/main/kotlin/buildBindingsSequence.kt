package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentFactoryModel.InputPayload
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ListDeclarationModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel.MultiBindingKind
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.allInputs
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.graph.BaseBinding
import com.yandex.daggerlite.graph.MultiBinding.ContributionType

/**
 * @param graph incomplete graph. All required fields must be initialized.
 */
internal fun buildBindingsSequence(
    graph: BindingGraphImpl,
): Sequence<BaseBinding> = sequence {
    // Gather bindings from modules
    val seenSubcomponents = hashSetOf<ComponentModel>()
    val multiBindings = linkedMapOf<NodeModel, MutableMap<NodeModel, ContributionType>>()
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
            bindingModel.multiBinding?.let { kind ->
                multiBindings.getOrPut(bindingModel.target, ::mutableMapOf)[binding.target] = when(kind) {
                    MultiBindingKind.Direct -> ContributionType.Element
                    MultiBindingKind.Flatten -> ContributionType.Collection
                }
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
    }
    // Gather bindings from factory
    for (dependency: ComponentDependencyModel in graph.model.dependencies) {
        // Binding for the dependency component itself.
        yield(ComponentDependencyBindingImpl(dependency = dependency, owner = graph))
        // Bindings backed by the component entry-points.
        for ((node: NodeModel, getter: FunctionLangModel) in dependency.exposedDependencies)
            yield(ComponentDependencyEntryPointBindingImpl(
                owner = graph,
                dependency = dependency,
                target = node,
                getter = getter,
            ))
    }
    graph.model.factory?.let { factory: ComponentFactoryModel ->
        for (input: ComponentFactoryModel.InputModel in factory.allInputs) when (val payload = input.payload) {
            is InputPayload.Instance -> yield(InstanceBindingImpl(target = payload.node, owner = graph))
            else -> {}
        }
    }

    val declaredLists: Map<NodeModel, ListDeclarationModel> = graph.modules.asSequence()
        .flatMap { it.listDeclarations }
        .onEach {
            // Provide empty map for an empty list
            multiBindings.getOrPut(it.listType, ::mutableMapOf)
        }
        .associateBy { it.listType } // TODO: [validation]: no duplicates are allowed

    // Multi-bindings
    for ((target: NodeModel, contributions: Map<NodeModel, ContributionType>) in multiBindings) {
        yield(MultiBindingImpl(
            owner = graph,
            target = target.multiBoundListNode(),
            contributions = contributions,
            declaration = declaredLists[target]
        ))
    }

    // This component binding
    yield(ComponentInstanceBindingImpl(graph = graph))
}