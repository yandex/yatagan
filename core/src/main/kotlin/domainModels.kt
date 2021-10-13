package com.yandex.dagger3.core

interface ClassBackedModel {
    val name: ClassNameModel
}

/**
 * Represents @[dagger.Component] annotated class - Component.
 */
interface ComponentModel : ClassBackedModel {
    val modules: Set<ModuleModel>

    val dependencies: Set<ComponentModel>
    val entryPoints: Set<EntryPointModel>

    val factory: ComponentFactoryModel?

    val isRoot: Boolean
}

interface ComponentFactoryModel : ClassBackedModel {
    val inputs: Collection<FactoryInput>
}

sealed interface FactoryInput {
    val target: ClassBackedModel
    val paramName: String
}

class ComponentDependency(
    override val target: ComponentModel,
    override val paramName: String,
) : FactoryInput

class ModuleInstance(
    override val target: ModuleModel,
    override val paramName: String,
) : FactoryInput

data class EntryPointModel(
    val getter: MemberCallableNameModel,
    val dep: NodeDependency,
) {
    override fun toString() = "$getter -> $dep"
}

interface ModuleModel : ClassBackedModel {
    val bindings: Collection<Binding>
    val subcomponents: Collection<ComponentModel>
}

interface NodeQualifier

interface NodeScope

interface NodeModel : ClassBackedModel {
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

sealed interface Binding {
    val target: NodeModel
    val scope: NodeScope?
}

class AliasBinding(
    override val target: NodeModel,
    override val scope: NodeScope?,
    val source: NodeModel,
) : Binding

sealed interface NonAliasBinding : Binding

class ProvisionBinding(
    override val target: NodeModel,
    override val scope: NodeScope?,
    val provider: CallableNameModel,
    val params: Collection<NodeDependency>,
) : NonAliasBinding

class InstanceBinding(
    override val target: NodeModel,
    override val paramName: String,
) : NonAliasBinding, FactoryInput {
    override val scope: Nothing? get() = null
}