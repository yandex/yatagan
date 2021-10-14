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

fun Binding.dependencies(): Collection<NodeModel.Dependency> = when (this) {
    is AliasBinding -> listOf(NodeModel.Dependency(source))
    is ProvisionBinding -> params
    is InstanceBinding -> emptyList()
}
