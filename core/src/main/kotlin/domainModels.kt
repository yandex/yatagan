package com.yandex.dagger3.core

/**
 * Represents @[dagger.Component] annotated class - Component.
 */
interface ComponentModel {
    val name: NameModel
    val modules: Set<ModuleModel>

    val dependencies: Set<ComponentModel>
    val entryPoints: Set<EntryPointModel>
}

data class EntryPointModel(
    val getter: MemberCallableNameModel,
    val dep: NodeDependency,
) {
    override fun toString() = "$getter -> $dep"
}

interface ModuleModel {
    val bindings: Collection<Binding>
}

interface NodeQualifier

interface NodeScope

interface NodeModel {
    val name: NameModel
    val qualifier: NodeQualifier?
    val defaultBinding: Binding?
}

data class NodeDependency(
    val node: NodeModel,
    val kind: Kind = Kind.Normal,
) {
    enum class Kind {
        Normal,
        Lazy,
        Provider,
    }

    override fun toString() = "$node [$kind]"

    companion object
}

sealed class Binding(
    val target: NodeModel,
    val scope: NodeScope?,
)

class AliasBinding(
    target: NodeModel,
    scope: NodeScope?,
    val source: NodeModel,
) : Binding(target, scope)

class ProvisionBinding(
    target: NodeModel,
    scope: NodeScope?,
    val provider: CallableNameModel,
    val params: Collection<NodeDependency>,
) : Binding(target, scope)
