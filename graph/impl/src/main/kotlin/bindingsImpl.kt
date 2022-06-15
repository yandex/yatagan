package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.AssistedInjectFactoryModel
import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.HasNodeModel
import com.yandex.daggerlite.core.InjectConstructorModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel.BindingTargetModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.accept
import com.yandex.daggerlite.core.component1
import com.yandex.daggerlite.core.component2
import com.yandex.daggerlite.core.isOptional
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.graph.AliasBinding
import com.yandex.daggerlite.graph.AlternativesBinding
import com.yandex.daggerlite.graph.AssistedInjectFactoryBinding
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
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.Strings.Errors
import com.yandex.daggerlite.validation.impl.ValidationMessageBuilder
import com.yandex.daggerlite.validation.impl.reportError
import com.yandex.daggerlite.validation.impl.reportWarning
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal interface BaseBindingMixin : BaseBinding {
    override val owner: BindingGraphImpl

    override val originModule: ModuleModel?
        get() = null
}

internal fun Binding.graphConditionScope(): ConditionScope = conditionScope and owner.conditionScope

internal interface BindingMixin : Binding, BaseBindingMixin {
    override val conditionScope: ConditionScope
        get() = ConditionScope.Unscoped

    override val scope: AnnotationLangModel?
        get() = null

    val checkDependenciesConditionScope: Boolean get() = false

    override fun validate(validator: Validator) {
        validator.child(conditionScope)
        dependencies.forEach {
            validator.child(owner.resolveRaw(it.node))
        }

        if (checkDependenciesConditionScope) {
            val conditionScope = graphConditionScope()
            for ((node, kind) in dependencies) {
                if (kind.isOptional) continue
                val resolved = owner.resolveBinding(node)
                val resolvedScope = resolved.graphConditionScope()
                if (resolvedScope !in conditionScope) {
                    validator.reportError(Errors.incompatibleCondition(
                        aCondition = resolvedScope, bCondition = conditionScope, a = node, b = this,
                    ))
                }
            }
        }

        if (scope != null && scope != owner.scope) {
            validator.reportError(Errors.noMatchingScopeForBinding(binding = this@BindingMixin, scope = scope))
        }
    }

    override val dependencies: Sequence<NodeDependency>
        get() = emptySequence()

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
        when (val target = impl.target) {
            is BindingTargetModel.DirectMultiContribution,
            is BindingTargetModel.FlattenMultiContribution,
            -> MultiBindingContributionNode(target.node)
            is BindingTargetModel.Plain -> target.node
        }
    }

    private class MultiBindingContributionNode(
        private val underlying: NodeModel,
    ) : NodeModel by underlying {
        override fun getSpecificModel(): Nothing? = null
        override fun toString() = "$underlying [multi-binding contributor]"
        override val node: NodeModel get() = this
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

    override val dependencies by lazy(PUBLICATION) {
        if (conditionScope.isNever) emptySequence() else inputs.asSequence()
    }

    override fun toString() = impl.toString()

    override val checkDependenciesConditionScope get() = true

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitProvision(this)
    }
}

internal class InjectConstructorProvisionBindingImpl(
    private val impl: InjectConstructorModel,
    override val owner: BindingGraphImpl,
) : ProvisionBinding, ConditionalBindingMixin {
    override val target get() = impl.asNode()
    override val originModule: Nothing? get() = null
    override val scope: AnnotationLangModel? get() = impl.scope
    override val provision get() = impl.constructor
    override val inputs: List<NodeDependency> get() = impl.inputs
    override val requiresModuleInstance: Boolean = false
    override val variantMatch: VariantMatch by lazy(NONE) { VariantMatch(impl, owner.variant) }

    override val checkDependenciesConditionScope: Boolean
        get() = true

    override val dependencies by lazy(PUBLICATION) {
        if (conditionScope.isNever) emptySequence() else impl.inputs.asSequence()
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        validator.inline(impl)
    }

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitProvision(this)
    }

    override fun toString() = impl.toString()
}

internal class AssistedInjectFactoryBindingImpl(
    override val owner: BindingGraphImpl,
    override val model: AssistedInjectFactoryModel,
) : AssistedInjectFactoryBinding, BindingMixin {
    override val target: NodeModel
        get() = model.asNode()

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitAssistedInjectFactory(this)
    }

    override val dependencies by lazy(PUBLICATION) {
        model.assistedConstructorParameters
            .asSequence()
            .filterIsInstance<AssistedInjectFactoryModel.Parameter.Injected>()
            .map { it.dependency }
            .memoize()
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        validator.inline(model)
    }

    override val checkDependenciesConditionScope get() = true

    override fun toString() = model.toString()
}

internal class SyntheticAliasBindingImpl(
    override val source: NodeModel,
    override val target: NodeModel,
    override val owner: BindingGraphImpl,
) : AliasBinding {
    override val originModule: ModuleModel? get() = null
    override fun <R> accept(visitor: BaseBinding.Visitor<R>): R {
        return visitor.visitAlias(this)
    }

    override fun validate(validator: Validator) {
        validator.inline(owner.resolveRaw(source))
    }

    override fun toString(): String {
        // Fully transparent alias
        return owner.resolveRaw(source).toString()
    }
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
        return this === other || (other is AliasBinding &&
                source == other.source && target == other.target &&
                owner == other.owner)
    }

    override fun hashCode(): Int {
        var result = target.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + owner.hashCode()
        return result
    }

    override fun toString() = "[alias] $impl"

    override fun validate(validator: Validator) {
        validator.child(owner.resolveRaw(source))
        if (impl.scope != null) {
            validator.reportWarning("Scope has no effect on 'alias' binding") {
                addNote("Scope is inherited from source graph node and can not be overridden")
            }
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

    override val conditionScope: ConditionScope by lazy(PUBLICATION) {
        alternatives.fold(ConditionScope.NeverScoped) { acc, alternative ->
            val binding = owner.resolveBinding(alternative)
            acc or binding.conditionScope
        }
    }

    override val dependencies get() = alternatives

    override fun toString() = "[alternatives] $impl"

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitAlternatives(this)
    }

    // TODO: issue warnings about unreachable alternatives
}

internal class ComponentDependencyEntryPointBindingImpl(
    override val owner: BindingGraphImpl,
    override val dependency: ComponentDependencyModel,
    override val getter: FunctionLangModel,
    override val target: NodeModel,
) : ComponentDependencyEntryPointBinding, BindingMixin {
    override fun toString() = Strings.Bindings.componentDependencyEntryPoint(getter)

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitComponentDependencyEntryPoint(this)
    }
}

internal class ComponentInstanceBindingImpl(
    graph: BindingGraphImpl,
) : ComponentInstanceBinding, BindingMixin {
    override val owner: BindingGraphImpl = graph
    override val target get() = owner.model.asNode()

    override fun toString() = Strings.Bindings.componentInstance(component = target)

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
            "Not reached: $this: Can't find child component $targetComponent among $owner's children."
        }
    }

    override val dependencies by lazy(PUBLICATION) {
        if (conditionScope.isNever) emptySequence()
        else targetGraph.usedParents.map { it.model.asNode() }.asSequence()
    }

    override val variantMatch: VariantMatch by lazy(NONE) {
        VariantMatch(factory.createdComponent, owner.variant)
    }

    override fun toString() = Strings.Bindings.subcomponentFactory(factory)

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitSubComponentFactory(this)
    }
}

internal class MultiBindingImpl(
    override val owner: BindingGraphImpl,
    override val target: NodeModel,
    contributions: Map<NodeModel, ContributionType>,
) : MultiBinding, BindingMixin {
    private val _contributions = contributions
    override val contributions: Map<NodeModel, ContributionType> by lazy(NONE) {
        // Resolve aliases as multi-bindings often work with @Binds
        val resolved = _contributions.mapKeys { (node, _) -> owner.resolveBinding(node).target }
        topologicalSort(
            nodes = resolved.keys,
            inside = owner,
        ).associateWith(resolved::getValue)
    }

    override val dependencies get() = _contributions.keys.asSequence()
    override val originModule: Nothing? get() = null

    override fun toString() = Strings.Bindings.multibinding(
        elementType = target,
        contributions = contributions.map { (node, _) -> owner.resolveRaw(node) }
    )

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitMulti(this)
    }
}

internal class ExplicitEmptyBindingImpl(
    override val impl: ModuleHostedBindingModel,
    override val owner: BindingGraphImpl,
) : EmptyBinding, BindingMixin, ModuleHostedMixin() {
    override val conditionScope get() = ConditionScope.NeverScoped
    override fun toString() = "[absent] $impl"

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }
}

internal class ComponentDependencyBindingImpl(
    override val dependency: ComponentDependencyModel,
    override val owner: BindingGraphImpl,
) : ComponentDependencyBinding, BindingMixin {
    override val target get() = dependency.asNode()

    override fun toString() = Strings.Bindings.componentDependencyInstance(dependency = dependency)

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitComponentDependency(this)
    }
}

internal class InstanceBindingImpl(
    override val target: NodeModel,
    override val owner: BindingGraphImpl,
    private val origin: ComponentFactoryModel.InputModel,
) : InstanceBinding, BindingMixin {

    override fun toString() = Strings.Bindings.instance(origin = origin)

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitInstance(this)
    }
}

internal class SelfDependentInvalidBinding(
    override val impl: ModuleHostedBindingModel,
    override val owner: BindingGraphImpl,
) : EmptyBinding, BindingMixin, ModuleHostedMixin() {
    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        // Always invalid
        validator.reportError(Errors.selfDependentBinding())
    }

    override fun toString() = "[invalid] $impl"
}

internal class AliasLoopStubBinding(
    override val owner: BindingGraphImpl,
    override val target: NodeModel,
    private val aliasLoop: Collection<AliasBinding>,
) : EmptyBinding, BindingMixin {
    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        validator.reportError(Errors.dependencyLoop(aliasLoop.map { it.target to it }))
    }

    override fun toString() = "[invalid] ${aliasLoop.first()}"
}

internal data class MissingBindingImpl(
    override val target: NodeModel,
    override val owner: BindingGraphImpl,
) : EmptyBinding, BindingMixin {

    override fun validate(validator: Validator) {
        target.getSpecificModel().accept(ModelBasedHint(validator))
        // TODO: implement hint about how to provide a binding
        //  - maybe the same differently qualified binding exists
        //  - binding exists in a sibling component hierarchy path
    }

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }

    override fun toString() = "[missing: $target]"

    private inner class ModelBasedHint(
        private val validator: Validator,
    ) : HasNodeModel.Visitor<Unit> {
        private inline fun reportMissingBinding(block: ValidationMessageBuilder.() -> Unit) {
            validator.reportError(Errors.missingBinding(`for` = target)) {
                if (target.hintIsFrameworkType) {
                    addNote(Strings.Notes.nestedFrameworkType(target))
                } else {
                    block()
                }
            }
        }

        override fun visitDefault() = reportMissingBinding {
            addNote(Strings.Notes.unknownBinding())
        }

        override fun visitInjectConstructor(model: InjectConstructorModel) {
            // This @inject was not used, the problem is in scope then.
            validator.reportError(Errors.noMatchingScopeForBinding(
                binding = model, scope = model.scope))
        }

        override fun visitComponent(model: ComponentModel) = reportMissingBinding {
            addNote("A dependency seems to be a component, though it does not belong to the current hierarchy.")
        }

        override fun visitAssistedInjectFactory(model: AssistedInjectFactoryModel): Nothing {
            throw AssertionError("Not reached: assisted inject factory can't be missing")
        }

        override fun visitComponentFactory(model: ComponentFactoryModel) = reportMissingBinding {
            addNote(if (!model.createdComponent.isRoot) {
                Strings.Notes.subcomponentFactoryInjectionHint(
                    factory = model,
                    component = model.createdComponent,
                    owner = owner,
                )
            } else {
                "$target is a factory for a root component, injecting such factory is not supported"
            })
        }
    }
}
