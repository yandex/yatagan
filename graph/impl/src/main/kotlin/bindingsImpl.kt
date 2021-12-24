package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.InjectConstructorBindingModel
import com.yandex.daggerlite.core.ListDeclarationModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.graph.AliasBinding
import com.yandex.daggerlite.graph.AlternativesBinding
import com.yandex.daggerlite.graph.BaseBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.ComponentDependencyBinding
import com.yandex.daggerlite.graph.ComponentDependencyEntryPointBinding
import com.yandex.daggerlite.graph.ComponentInstanceBinding
import com.yandex.daggerlite.graph.ConditionScope
import com.yandex.daggerlite.graph.EmptyBinding
import com.yandex.daggerlite.graph.InstanceBinding
import com.yandex.daggerlite.graph.MultiBinding
import com.yandex.daggerlite.graph.MultiBinding.ContributionType
import com.yandex.daggerlite.graph.ProvisionBinding
import com.yandex.daggerlite.graph.SubComponentFactoryBinding
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.addNote
import com.yandex.daggerlite.validation.impl.buildError
import com.yandex.daggerlite.validation.impl.buildWarning
import kotlin.LazyThreadSafetyMode.NONE

internal interface BaseBindingMixin : BaseBinding {
    override val owner: BindingGraphImpl

    override val originModule: ModuleModel?
        get() = null
}

internal interface BindingMixin : Binding, BaseBindingMixin {
    override val conditionScope: ConditionScope
        get() = ConditionScope.Unscoped

    override val scope: AnnotationLangModel?
        get() = null

    override fun validate(validator: Validator) {
        validator.child(conditionScope)
        dependencies().forEach {
            validator.child(owner.resolveRaw(it.node))
        }
    }

    override fun dependencies(): Collection<NodeDependency> = emptyList()

    override fun <R> accept(visitor: BaseBinding.Visitor<R>): R {
        return visitor.visitBinding(this)
    }
}

internal interface ConditionalBindingMixin : BindingMixin {
    val variantMatch: VariantMatch

    override val conditionScope: ConditionScope
        get() = variantMatch.conditionScope

    override fun validate(validator: Validator) {
        super.validate(validator)
        when (val match = variantMatch) {
            is VariantMatch.Error -> validator.report(match.message)
            is VariantMatch.Matched -> {}
        }
    }
}

internal abstract class ModuleHostedMixin : BaseBindingMixin {
    abstract val impl: ModuleHostedBindingModel

    final override val originModule get() = impl.originModule

    final override val target: NodeModel by lazy(NONE) {
        if (impl.multiBinding != null)
            MultiBindingContributionNode(impl.target)
        else impl.target
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
) : ProvisionBinding, ConditionalBindingMixin, ModuleHostedMixin() {

    override val scope get() = impl.scope
    override val provision get() = impl.provision
    override val inputs get() = impl.inputs
    override val requiresModuleInstance get() = impl.requiresModuleInstance
    override val variantMatch: VariantMatch by lazy(NONE) { VariantMatch(impl, owner.variant) }

    override fun dependencies(): Collection<NodeDependency> {
        return if (conditionScope.isNever) emptyList() else inputs.toList()
    }

    override fun toString() = "@Provides ${inputs.toList()} -> $target"

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitProvision(this)
    }
}

internal class InjectConstructorProvisionBindingImpl(
    private val impl: InjectConstructorBindingModel,
    override val owner: BindingGraphImpl,
) : ProvisionBinding, ConditionalBindingMixin {
    override val target get() = impl.target
    override val originModule: Nothing? get() = null
    override val scope: AnnotationLangModel? get() = impl.scope
    override val provision get() = impl.constructor
    override val inputs: Sequence<NodeDependency> get() = impl.inputs
    override val requiresModuleInstance: Boolean = false
    override val variantMatch: VariantMatch by lazy(NONE) { VariantMatch(impl, owner.variant) }

    override fun dependencies(): Collection<NodeDependency> {
        return if (conditionScope.isNever) emptyList() else impl.inputs.toList()
    }

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitProvision(this)
    }

    override fun toString() = "@Inject $target"
}

internal class AliasBindingImpl(
    override val impl: BindsBindingModel,
    override val owner: BindingGraphImpl,
) : AliasBinding, ModuleHostedMixin() {
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

    override fun validate(validator: Validator) {
        validator.child(source)
        if (impl.scope != null) {
            validator.report(buildWarning {
                this.contents = "Scope has no effect on 'alias' binding"
                this.addNote {
                    this.contents = "Scope is inherited from source graph node and can not be overridden"
                }
            })
        }
    }

    override fun <R> accept(visitor: BaseBinding.Visitor<R>): R {
        return visitor.visitAlias(this)
    }
}

internal class AlternativesBindingImpl(
    override val impl: BindsBindingModel,
    override val owner: BindingGraphImpl,
) : AlternativesBinding, BindingMixin, ModuleHostedMixin() {
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

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitAlternatives(this)
    }
}

internal class ComponentDependencyEntryPointBindingImpl(
    override val owner: BindingGraphImpl,
    override val dependency: ComponentDependencyModel,
    override val getter: FunctionLangModel,
    override val target: NodeModel,
) : ComponentDependencyEntryPointBinding, BindingMixin {
    override fun dependencies() = listOf(NodeDependency(dependency.asNode()))

    override fun toString() = "$getter from $dependency (intrinsic)"

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitComponentDependencyEntryPoint(this)
    }
}

internal class ComponentInstanceBindingImpl(
    graph: BindingGraphImpl,
) : ComponentInstanceBinding, BindingMixin {
    override val owner: BindingGraphImpl = graph
    override val target get() = owner.model.asNode()

    override fun toString() = "Component instance $target (intrinsic)"

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitComponentInstance(this)
    }
}

internal class SubComponentFactoryBindingImpl(
    override val owner: BindingGraphImpl,
    private val factory: ComponentFactoryModel,
) : SubComponentFactoryBinding, ConditionalBindingMixin {
    override val target: NodeModel
        get() = factory.asNode()

    override val targetGraph: BindingGraph by lazy(NONE) {
        val targetComponent = factory.createdComponent
        checkNotNull(owner.children.find { it.model == targetComponent }) {
            "$this: Can't find child component $targetComponent among $owner's children."
        }
    }

    override fun dependencies(): List<NodeDependency> {
        return if (conditionScope.isNever) emptyList()
        else targetGraph.usedParents.map { NodeDependency(it.model.asNode()) }
    }

    override val variantMatch: VariantMatch by lazy(NONE) {
        VariantMatch(factory.createdComponent, owner.variant)
    }

    override fun toString() = "Subcomponent factory $factory (intrinsic)"

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitSubComponentFactory(this)
    }
}

internal class MultiBindingImpl(
    override val owner: BindingGraphImpl,
    override val target: NodeModel,
    contributions: Map<NodeModel, ContributionType>,
    declaration: ListDeclarationModel?,
) : MultiBinding, BindingMixin {
    private val _contributions = contributions
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

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitMulti(this)
    }
}

internal class ModuleHostedEmptyBindingImpl(
    override val impl: ModuleHostedBindingModel,
    override val owner: BindingGraphImpl,
) : EmptyBinding, BindingMixin, ModuleHostedMixin() {
    override val conditionScope get() = ConditionScope.NeverScoped
    override fun toString() = "Absent $target in $impl"

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }
}

internal class ComponentDependencyBindingImpl(
    override val dependency: ComponentDependencyModel,
    override val owner: BindingGraphImpl,
) : ComponentDependencyBinding, BindingMixin {
    override val target get() = dependency.asNode()

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitComponentDependency(this)
    }
}

internal class InstanceBindingImpl(
    override val target: NodeModel,
    override val owner: BindingGraphImpl,
) : InstanceBinding, BindingMixin {

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitInstance(this)
    }
}

internal data class MissingBindingImpl(
    override val target: NodeModel,
    override val owner: BindingGraphImpl,
) : EmptyBinding, BindingMixin {
    override val conditionScope get() = ConditionScope.NeverScoped

    override fun validate(validator: Validator) {
        validator.report(buildError {
            contents = target.implicitBinding?.let { failedInject ->
                "$target has an @Inject constructor, though no components in the " +
                        "hierarchy matched its scope ${failedInject.scope}"
            } ?: "Missing binding for $target, no known way to create it"
        })
    }

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }

    override fun toString() = "[missing: $target]"
}