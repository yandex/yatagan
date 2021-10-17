package com.yandex.dagger3.core

import kotlin.contracts.contract

fun Binding.isScoped(): Boolean {
    contract { returns(true) implies (this@isScoped is ProvisionBinding) }
    return scope() != null
}

fun Binding.scope(): ProvisionBinding.Scope? {
    contract { returnsNotNull() implies (this@scope is ProvisionBinding) }
    return when (this) {
        is ProvisionBinding -> scope
        else -> null
    }
}

// MAYBE: make this a regular virtual function
internal fun Binding.dependencies(): Collection<NodeModel.Dependency> = when (this) {
    is AliasBinding -> listOf(NodeModel.Dependency(source))
    is ProvisionBinding -> params
    is ComponentInstanceBinding,
    is InstanceBinding -> emptyList()

    // fixme: target.target.graph.usedParents.map { NodeModel.Dependency(it.component) }
    is SubComponentFactoryBinding -> emptyList()
}

/**
 * Utility wrapper around [BindingGraph.resolveBinding] which follows alias bindings.
 * @see BindingGraph.resolveBinding
 */
fun BindingGraph.resolveNonAliasBinding(node: NodeModel): NonAliasBinding {
    var binding: Binding = resolveBinding(node)
    while (true) {
        binding = when (binding) {
            is AliasBinding -> resolveBinding(binding.source)
            is NonAliasBinding -> return binding
        }
    }
}

fun BindingGraph.resolveNonAliasBinding(maybeAlias: Binding): NonAliasBinding {
    var binding: Binding = maybeAlias
    while (true) {
        binding = when (binding) {
            is AliasBinding -> resolveBinding(binding.source)
            is NonAliasBinding -> return binding
        }
    }
}
