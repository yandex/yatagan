package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.ComponentDependencyInput
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.InjectConstructorBindingModel
import com.yandex.daggerlite.core.InstanceInput
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.graph.AliasBinding
import com.yandex.daggerlite.graph.AlternativesBinding
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.BootstrapListBinding
import com.yandex.daggerlite.graph.ComponentDependencyBinding
import com.yandex.daggerlite.graph.ComponentDependencyEntryPointBinding
import com.yandex.daggerlite.graph.ComponentInstanceBinding
import com.yandex.daggerlite.graph.ConditionScope
import com.yandex.daggerlite.graph.EmptyBinding
import com.yandex.daggerlite.graph.InstanceBinding
import com.yandex.daggerlite.graph.ProvisionBinding
import com.yandex.daggerlite.graph.SubComponentFactoryBinding
import kotlin.LazyThreadSafetyMode.NONE

internal class ProvisionBindingImpl(
    private val impl: ProvidesBindingModel,
    override val owner: BindingGraph,
    override val conditionScope: ConditionScope,
) : ProvisionBinding {

    override val target get() = impl.target
    override val originModule get() = impl.originModule
    override val scope get() = impl.scope
    override val provision get() = impl.provision
    override val inputs get() = impl.inputs
    override val requiresModuleInstance get() = impl.requiresModuleInstance

    override fun dependencies(): Collection<NodeDependency> = inputs.toList()
    override fun toString() = "@Provides ${inputs.toList()} -> $target"
}

internal class InjectConstructorProvisionBindingImpl(
    private val impl: InjectConstructorBindingModel,
    override val owner: BindingGraph,
    override val conditionScope: ConditionScope,
) : ProvisionBinding {
    override val target get() = impl.target
    override val originModule: Nothing? get() = null
    override val scope: AnnotationLangModel? get() = impl.scope
    override val provision get() = impl.constructor
    override val inputs: Sequence<NodeDependency> get() = impl.inputs
    override val requiresModuleInstance: Boolean = false

    override fun dependencies(): Collection<NodeDependency> {
        return impl.inputs.toList()
    }
}

internal class AliasBindingImpl(
    private val impl: BindsBindingModel,
    override val owner: BindingGraph,
) : AliasBinding {
    init {
        require(impl.scope == null)
        require(impl.sources.count() == 1)
    }

    override val target get() = impl.target
    override val source get() = impl.sources.single()
    override val originModule get() = impl.originModule

    override fun equals(other: Any?): Boolean {
        return this === other || (other is AliasBindingImpl &&
                source == other.source && target == other.target)
    }

    override fun hashCode(): Int {
        var result = target.hashCode()
        result = 31 * result + source.hashCode()
        return result
    }

    override fun toString() = "@Binds (alias) $source -> $target"
}

internal class AlternativesBindingImpl(
    private val impl: BindsBindingModel,
    override val owner: BindingGraph,
) : AlternativesBinding {
    override val target get() = impl.target
    override val originModule get() = impl.originModule
    override val scope get() = impl.scope
    override val alternatives get() = impl.sources

    override val conditionScope: ConditionScope by lazy(NONE) {
        alternatives.fold(ConditionScope.NeverScoped) { acc, alternative ->
            val binding = owner.resolveBinding(alternative)
            acc or binding.conditionScope
        }
    }

    override fun dependencies() = alternatives.map(::NodeDependency).toList()

    override fun toString() = "@Binds (alternatives) [first present of $alternatives] -> $target"
}

internal class ComponentDependencyEntryPointBindingImpl(
    override val owner: BindingGraph,
    override val input: ComponentDependencyInput,
    private val entryPoint: ComponentModel.EntryPoint,
) : ComponentDependencyEntryPointBinding {
    init {
        require(entryPoint.dependency.kind == DependencyKind.Direct) {
            "Only direct entry points constitute a binding that can be used in dependency components"
        }
    }

    override val originModule: Nothing? get() = null
    override val scope: Nothing? get() = null
    override val conditionScope get() = ConditionScope.Unscoped
    override val target get() = entryPoint.dependency.node
    override val getter get() = entryPoint.getter
    override fun dependencies() = listOf(NodeDependency(input.component.asNode()))

    override fun toString() = "$entryPoint from ${input.component} (intrinsic)"
}

internal class ComponentInstanceBindingImpl(
    graph: BindingGraph,
) : ComponentInstanceBinding {
    override val owner: BindingGraph = graph
    override val target get() = owner.model.asNode()
    override val scope: Nothing? get() = null
    override val conditionScope get() = ConditionScope.Unscoped
    override fun dependencies(): List<Nothing> = emptyList()
    override val originModule: Nothing? get() = null

    override fun toString() = "Component instance $target (intrinsic)"
}

internal class SubComponentFactoryBindingImpl(
    override val owner: BindingGraph,
    override val conditionScope: ConditionScope,
    private val factory: ComponentFactoryModel,
) : SubComponentFactoryBinding {
    override val target: NodeModel
        get() = factory.asNode()

    override val targetGraph: BindingGraph by lazy(NONE) {
        val targetComponent = factory.createdComponent
        checkNotNull(owner.children.find { it.model == targetComponent }) {
            "$this: Can't find child component $targetComponent among $owner's children."
        }
    }

    override val scope: Nothing? get() = null

    override val originModule: Nothing? get() = null
    override fun dependencies() = targetGraph.usedParents.map { NodeDependency(it.model.asNode()) }
    override fun toString() = "Subcomponent factory $factory (intrinsic)"
}

internal class BootstrapListBindingImpl(
    override val owner: BindingGraph,
    override val target: NodeModel,
    private val inputs: Collection<NodeModel>,
) : BootstrapListBinding {
    override val scope: Nothing? get() = null
    override val conditionScope get() = ConditionScope.Unscoped
    override fun dependencies() = inputs.map(::NodeDependency)
    override val list: Collection<NodeModel> by lazy(NONE) {
        topologicalSort(nodes = inputs, inside = owner)
    }
    override val originModule: Nothing? get() = null

    override fun toString() = "Bootstrap $target of ${inputs.take(3)}${if (inputs.size > 3) "..." else ""} (intrinsic)"
}

internal class EmptyBindingImpl(
    override val owner: BindingGraph,
    override val target: NodeModel,
    override val originModule: ModuleModel? = null,
    val reason: String,
) : EmptyBinding {
    override val scope: Nothing? get() = null
    override val conditionScope get() = ConditionScope.NeverScoped
    override fun dependencies(): List<Nothing> = emptyList()

    override fun toString() = "Absent $target because $reason"
}

internal class ComponentDependencyBindingImpl(
    override val input: ComponentDependencyInput,
    override val owner: BindingGraph,
) : ComponentDependencyBinding {
    override val target get() = input.component.asNode()
    override val conditionScope get() = ConditionScope.Unscoped
    override val scope: Nothing? get() = null
    override fun dependencies(): List<Nothing> = emptyList()
    override val originModule: Nothing? get() = null
}

internal class InstanceBindingImpl(
    override val input: InstanceInput,
    override val owner: BindingGraph,
) : InstanceBinding {
    override val conditionScope get() = ConditionScope.Unscoped
    override val scope: Nothing? get() = null
    override val target get() = input.node
    override fun dependencies(): List<Nothing> = emptyList()
    override val originModule: Nothing? get() = null
}