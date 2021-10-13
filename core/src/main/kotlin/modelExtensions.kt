package com.yandex.dagger3.core

val Binding.isScoped get() = scope != null

fun Binding.dependencies(): Collection<NodeModel.Dependency> = when (this) {
    is AliasBinding -> listOf(NodeModel.Dependency(source))
    is ProvisionBinding -> params
    is InstanceBinding -> emptyList()
}
