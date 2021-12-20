package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.InjectConstructorBindingModel
import com.yandex.daggerlite.core.ListDeclarationModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.graph.AliasBinding
import com.yandex.daggerlite.graph.AlternativesBinding
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.ComponentDependencyBinding
import com.yandex.daggerlite.graph.ComponentDependencyEntryPointBinding
import com.yandex.daggerlite.graph.ComponentInstanceBinding
import com.yandex.daggerlite.graph.ConditionScope
import com.yandex.daggerlite.graph.EmptyBinding
import com.yandex.daggerlite.graph.InstanceBinding
import com.yandex.daggerlite.graph.MissingBinding
import com.yandex.daggerlite.graph.MultiBinding
import com.yandex.daggerlite.graph.MultiBinding.ContributionType
import com.yandex.daggerlite.graph.ProvisionBinding
import com.yandex.daggerlite.graph.SubComponentFactoryBinding
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.addNote
import com.yandex.daggerlite.validation.impl.buildError
import com.yandex.daggerlite.validation.impl.buildWarning
import kotlin.LazyThreadSafetyMode.NONE

internal abstract class BindingBase : MayBeInvalid {
    protected abstract fun dependencies(): Collection<NodeDependency>
    protected abstract val owner: BindingGraphImpl

    override fun validate(validator: Validator) {
        dependencies().forEach {
            validator.child(owner.resolveRaw(it.node))
        }
    }
}

internal abstract class ModuleHostedBindingBase : BindingBase() {
    protected abstract val impl: ModuleHostedBindingModel

    val originModule get() = impl.originModule

    val target: NodeModel by lazy(NONE) {
        with(impl) {
            if (multiBinding != null)
                MultiBindingContributionNode(target)
            else target
        }
    }

    private class MultiBindingContributionNode(
        private val node: NodeModel,
    ) : NodeModel by node {
        override val implicitBinding: Nothing? get() = null
        override fun toString() = "$node [multi-binding contributor]"
    }
}

internal class ProvisionBindingImpl(
    override val impl: ProvidesBindingModel,
    override val owner: BindingGraphImpl,
    override val conditionScope: ConditionScope,
) : ProvisionBinding, ModuleHostedBindingBase() {

    override val scope get() = impl.scope
    override val provision get() = impl.provision
    override val inputs get() = impl.inputs
    override val requiresModuleInstance get() = impl.requiresModuleInstance

    override fun dependencies(): Collection<NodeDependency> = inputs.toList()
    override fun toString() = "@Provides ${inputs.toList()} -> $target"
}

internal class InjectConstructorProvisionBindingImpl(
    private val impl: InjectConstructorBindingModel,
    override val owner: BindingGraphImpl,
    override val conditionScope: ConditionScope,
) : ProvisionBinding, BindingBase() {
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
    override val impl: BindsBindingModel,
    override val owner: BindingGraphImpl,
) : AliasBinding, ModuleHostedBindingBase() {
    init {
        require(impl.sources.count() == 1) {
            "Not reached: sources count must be equal to 1"
        }
    }

    override val source get() = impl.sources.single()

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

    override fun dependencies(): Collection<NodeDependency> {
        return listOf(NodeDependency(source))
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        if (impl.scope != null) {
            validator.report(buildWarning {
                this.contents = "Scope has no effect on 'alias' binding"
                this.addNote {
                    this.contents = "Scope is inherited from source graph node and can not be overridden"
                }
            })
        }
    }
}

internal class AlternativesBindingImpl(
    override val impl: BindsBindingModel,
    override val owner: BindingGraphImpl,
) : AlternativesBinding, ModuleHostedBindingBase() {
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
    override val owner: BindingGraphImpl,
    override val dependency: ComponentDependencyModel,
    override val getter: FunctionLangModel,
    override val target: NodeModel,
) : ComponentDependencyEntryPointBinding, BindingBase() {
    override val originModule: Nothing? get() = null
    override val scope: Nothing? get() = null
    override val conditionScope get() = ConditionScope.Unscoped
    override fun dependencies() = listOf(NodeDependency(dependency.asNode()))

    override fun toString() = "$getter from $dependency (intrinsic)"
}

internal class ComponentInstanceBindingImpl(
    graph: BindingGraphImpl,
) : ComponentInstanceBinding, BindingBase() {
    override val owner: BindingGraphImpl = graph
    override val target get() = owner.model.asNode()
    override val scope: Nothing? get() = null
    override val conditionScope get() = ConditionScope.Unscoped
    override fun dependencies(): List<Nothing> = emptyList()
    override val originModule: Nothing? get() = null

    override fun toString() = "Component instance $target (intrinsic)"
}

internal class SubComponentFactoryBindingImpl(
    override val owner: BindingGraphImpl,
    override val conditionScope: ConditionScope,
    private val factory: ComponentFactoryModel,
) : SubComponentFactoryBinding, BindingBase() {
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

internal class MultiBindingImpl(
    override val owner: BindingGraphImpl,
    override val target: NodeModel,
    contributions: Map<NodeModel, ContributionType>,
    declaration: ListDeclarationModel?,
) : MultiBinding, BindingBase() {
    private val _contributions = contributions
    override val scope: Nothing? get() = null
    override val conditionScope get() = ConditionScope.Unscoped
    override val contributions: Map<NodeModel, ContributionType> by lazy(NONE) {
        if (declaration?.orderByDependency == true) {
            // Resolve aliases as multi-bindings often work with @Binds
            val resolved = _contributions.mapKeys { (node, _) -> owner.resolveBinding(node).target }
            topologicalSort(
                nodes = resolved.keys,
                inside = owner,
            ).associateWith(resolved::getValue)
        } else {
            _contributions
        }
    }

    override fun dependencies() = _contributions.keys.map(::NodeDependency)
    override val originModule: Nothing? get() = null

    override fun toString() =
        "MultiBinding $target of ${contributions.keys.take(3)}${if (contributions.size > 3) "..." else ""} (intrinsic)"
}

internal class ModuleHostedEmptyBindingImpl(
    override val impl: ModuleHostedBindingModel,
    override val owner: BindingGraphImpl,
) : EmptyBinding, ModuleHostedBindingBase() {
    override val conditionScope get() = ConditionScope.NeverScoped
    override val scope: Nothing? get() = null
    override fun dependencies(): List<Nothing> = emptyList()
    override fun toString() = "Absent $target in $impl"
}

internal class ImplicitEmptyBindingImpl(
    override val owner: BindingGraphImpl,
    override val target: NodeModel,
) : EmptyBinding, BindingBase() {
    override val scope: Nothing? get() = null
    override val conditionScope get() = ConditionScope.NeverScoped
    override fun dependencies(): List<Nothing> = emptyList()
    override fun toString() = "Absent $target (intrinsic)"
    override val originModule: Nothing? get() = null
}

internal class ComponentDependencyBindingImpl(
    override val dependency: ComponentDependencyModel,
    override val owner: BindingGraphImpl,
) : ComponentDependencyBinding, BindingBase() {
    override val target get() = dependency.asNode()
    override val conditionScope get() = ConditionScope.Unscoped
    override val scope: Nothing? get() = null
    override fun dependencies(): List<Nothing> = emptyList()
    override val originModule: Nothing? get() = null
}

internal class InstanceBindingImpl(
    override val target: NodeModel,
    override val owner: BindingGraphImpl,
) : InstanceBinding, BindingBase() {
    override val conditionScope get() = ConditionScope.Unscoped
    override val scope: Nothing? get() = null
    override fun dependencies(): List<Nothing> = emptyList()
    override val originModule: Nothing? get() = null
}

internal data class MissingBindingImpl(
    override val target: NodeModel,
    override val owner: BindingGraphImpl,
) : MissingBinding, BindingBase() {
    override val conditionScope get() = ConditionScope.NeverScoped
    override val scope: Nothing? get() = null
    override fun dependencies(): List<Nothing> = emptyList()
    override val originModule: Nothing? get() = null

    override fun validate(validator: Validator) {
        validator.report(buildError { this.contents = "Missing binding for ${this@MissingBindingImpl.target}" })
    }
}