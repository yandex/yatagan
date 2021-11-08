package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.AliasBinding
import com.yandex.daggerlite.core.AlternativesBinding
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.BootstrapListBinding
import com.yandex.daggerlite.core.ComponentDependencyEntryPointBinding
import com.yandex.daggerlite.core.ComponentDependencyInput
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentInstanceBinding
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.EmptyBinding
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvisionBinding
import com.yandex.daggerlite.core.SubComponentFactoryBinding
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import kotlin.LazyThreadSafetyMode.NONE

internal class ProvisionBindingImpl(
    override val owner: BindingGraph,
    override val target: NodeModel,
    override val scope: AnnotationLangModel?,
    override val provider: FunctionLangModel,
    override val params: Collection<NodeDependency>,
    override val requiredModuleInstance: ModuleModel?,
    override val conditionScope: ConditionScope,
) : ProvisionBinding {
    override fun dependencies() = params

    override fun toString() = "@Provides $params -> $target"
}

internal class AliasBindingImpl(
    override val owner: BindingGraph,
    override val target: NodeModel,
    override val source: NodeModel,
) : AliasBinding {
    override fun toString() = "@Binds (alias) $source -> $target"
}

internal class AlternativesBindingImpl(
    override val owner: BindingGraph,
    override val target: NodeModel,
    override val scope: AnnotationLangModel?,
    override val alternatives: Collection<NodeModel>,
) : AlternativesBinding {
    override val conditionScope: ConditionScope by lazy(NONE) {
        alternatives.fold(ConditionScope.NeverScoped) { acc, alternative ->
            val binding = owner.resolveBinding(alternative)
            acc or binding.conditionScope
        }
    }

    override fun dependencies() = alternatives.map(::NodeDependency)

    override fun toString() = "@Binds (alternatives) [first present of $alternatives] -> $target"
}

internal class ComponentDependencyEntryPointBindingImpl(
    override val owner: BindingGraph,
    override val input: ComponentDependencyInput,
    private val entryPoint: ComponentModel.EntryPoint,
) : ComponentDependencyEntryPointBinding {
    init {
        require(entryPoint.dependency.kind == DependencyKind.Direct) {
            // MAYBE: Implement some best-effort matching to available dependency kinds?
            "Only direct entry points constitute a binding that can be used in dependency components"
        }
    }

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
        checkNotNull(owner.children.find { it.model == targetComponent })
    }

    override val scope: Nothing? get() = null

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

    override fun toString() = "Bootstrap $target of ${inputs.take(3)}${if (inputs.size > 3) "..." else ""} (intrinsic)"
}

internal class EmptyBindingImpl(
    override val owner: BindingGraph,
    override val target: NodeModel,
) : EmptyBinding {
    override val scope: Nothing? get() = null
    override val conditionScope get() = ConditionScope.Unscoped
    override fun dependencies(): List<Nothing> = emptyList()

    override fun toString() = "Absent $target (intrinsic)"
}
