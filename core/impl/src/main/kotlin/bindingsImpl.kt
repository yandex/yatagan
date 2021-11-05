package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.core.AliasBinding
import com.yandex.daggerlite.core.AlternativesBinding
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.ComponentDependencyEntryPointBinding
import com.yandex.daggerlite.core.ComponentDependencyInput
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentInstanceBinding
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.EmptyBinding
import com.yandex.daggerlite.core.ModuleModel
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
    override val params: Collection<NodeModel.Dependency>,
    override val requiredModuleInstance: ModuleModel?,
    override val conditionScope: ConditionScope,
) : ProvisionBinding

internal class AliasBindingImpl(
    override val owner: BindingGraph,
    override val target: NodeModel,
    override val source: NodeModel,
) : AliasBinding

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
}

internal class ComponentDependencyEntryPointBindingImpl(
    override val owner: BindingGraph,
    override val input: ComponentDependencyInput,
    private val entryPoint: ComponentModel.EntryPoint,
) : ComponentDependencyEntryPointBinding {
    init {
        require(entryPoint.dependency.kind == NodeModel.Dependency.Kind.Direct) {
            // MAYBE: Implement some best-effort matching to available dependency kinds?
            "Only direct entry points constitute a binding that can be used in dependency components"
        }
    }

    override val scope: Nothing? get() = null
    override val conditionScope get() = ConditionScope.Unscoped
    override val target get() = entryPoint.dependency.node
    override val getter get() = entryPoint.getter
}

internal class ComponentInstanceBindingImpl(
    graph: BindingGraph,
) : ComponentInstanceBinding {
    override val owner: BindingGraph = graph
    override val target get() = owner.model.asNode()
    override val scope: Nothing? get() = null
    override val conditionScope get() = ConditionScope.Unscoped
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
}

internal class EmptyBindingImpl(
    override val owner: BindingGraph,
    override val target: NodeModel,
) : EmptyBinding {
    override val scope: Nothing? get() = null
    override val conditionScope get() = ConditionScope.Unscoped
}
