package com.yandex.dagger3.core

val Binding.isScoped get() = scope != null

fun Binding.dependencies(): Collection<NodeDependency> = when (this) {
    is AliasBinding -> listOf(NodeDependency(source))
    is ProvisionBinding -> params
}
