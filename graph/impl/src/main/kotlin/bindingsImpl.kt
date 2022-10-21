package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.base.ListComparator
import com.yandex.daggerlite.base.MapComparator
import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.base.notIntersects
import com.yandex.daggerlite.core.AssistedInjectFactoryModel
import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.CollectionTargetKind
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ConditionScope
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
import com.yandex.daggerlite.core.isNever
import com.yandex.daggerlite.core.isOptional
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.graph.AliasBinding
import com.yandex.daggerlite.graph.AlternativesBinding
import com.yandex.daggerlite.graph.AssistedInjectFactoryBinding
import com.yandex.daggerlite.graph.BaseBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.ComponentDependencyBinding
import com.yandex.daggerlite.graph.ComponentDependencyEntryPointBinding
import com.yandex.daggerlite.graph.ComponentInstanceBinding
import com.yandex.daggerlite.graph.EmptyBinding
import com.yandex.daggerlite.graph.ExtensibleBinding
import com.yandex.daggerlite.graph.InstanceBinding
import com.yandex.daggerlite.graph.MapBinding
import com.yandex.daggerlite.graph.MultiBinding
import com.yandex.daggerlite.graph.MultiBinding.ContributionType
import com.yandex.daggerlite.graph.ProvisionBinding
import com.yandex.daggerlite.graph.SubComponentFactoryBinding
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.Strings.Errors
import com.yandex.daggerlite.validation.format.TextColor
import com.yandex.daggerlite.validation.format.ValidationMessageBuilder
import com.yandex.daggerlite.validation.format.append
import com.yandex.daggerlite.validation.format.appendRichString
import com.yandex.daggerlite.validation.format.bindingModelRepresentation
import com.yandex.daggerlite.validation.format.buildRichString
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError
import com.yandex.daggerlite.validation.format.reportMandatoryWarning
import kotlin.LazyThreadSafetyMode.PUBLICATION

internal interface BaseBindingMixin : BaseBinding {
    override val originModule: ModuleModel?
        get() = null
}

internal fun Binding.graphConditionScope(): ConditionScope = conditionScope and owner.conditionScope

internal fun BindingGraph.resolveAliasChain(node: NodeModel): List<AliasBinding> = buildList {
    var maybeAlias = resolveBindingRaw(node)
    while (maybeAlias is AliasBinding) {
        add(maybeAlias)
        maybeAlias = resolveBindingRaw(maybeAlias.source)
    }
}

internal interface BindingMixin : Binding, BaseBindingMixin {
    override val conditionScope: ConditionScope
        get() = ConditionScope.Unscoped

    override val nonStaticConditionProviders: Set<NodeModel>
        get() = emptySet()

    override val scopes: Set<AnnotationLangModel>
        get() = emptySet()

    val nonStaticConditionDependencies: NonStaticConditionDependencies?
        get() = null

    /**
     * `true` if the binding requires the dependencies of compatible condition scope, and it's an error to
     *  provide a dependency under incompatible condition.
     *
     *  `false` if the binding allows dependencies of incompatible scope, e.g. because it can just skip them.
     */
    val checkDependenciesConditionScope: Boolean get() = false

    override fun validate(validator: Validator) {
        dependencies.forEach {
            validator.child(owner.resolveBindingRaw(it.node))
        }
        nonStaticConditionDependencies?.let(validator::child)

        if (checkDependenciesConditionScope) {
            val conditionScope = graphConditionScope()
            for (dependency in dependencies) {
                val (node, kind) = dependency
                if (kind.isOptional) continue
                val resolved = owner.resolveBinding(node)
                val resolvedScope = resolved.graphConditionScope()
                if (resolvedScope !in conditionScope) {
                    // Incompatible condition!
                    validator.reportError(Errors.incompatibleCondition(
                        aCondition = resolvedScope,
                        bCondition = conditionScope,
                        a = resolved,
                        b = this,
                    )) {
                        val aliases = owner.resolveAliasChain(node)
                        if (aliases.isNotEmpty()) {
                            addNote(Strings.Notes.conditionPassedThroughAliasChain(aliases = aliases))
                        }
                    }
                }
            }
        }

        if (scopes.isNotEmpty() && scopes notIntersects owner.scopes) {
            validator.reportError(Errors.noMatchingScopeForBinding(binding = this, scopes = scopes))
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

    override val nonStaticConditionDependencies: NonStaticConditionDependencies

    override val nonStaticConditionProviders: Set<NodeModel>
        get() = nonStaticConditionDependencies.conditionProviders

    override val conditionScope: ConditionScope
        get() = variantMatch.conditionScope

    override fun validate(validator: Validator) {
        super.validate(validator)
        when (val match = variantMatch) {
            is VariantMatch.Error -> match.message?.let { error -> validator.report(error) }
            is VariantMatch.Matched -> {}
        }
    }
}

internal sealed interface ComparableBindingMixin<B : ComparableBindingMixin<B>> : BaseBinding {
    fun compareTo(other: B): Int

    override fun compareTo(other: BaseBinding): Int {
        if (this == other) return 0
        other as ComparableBindingMixin<*>

        orderByClass(this).compareTo(orderByClass(other)).let { if (it != 0) return it }

        // If the class order is the same, assume the class is matching, delegate to dedicated comparison.
        @Suppress("UNCHECKED_CAST")
        return compareTo(other as B)
    }

    private companion object {
        // NOTE: These priorities denote a default order for contributions in a multi-binding - do not change these.
        fun orderByClass(binding: ComparableBindingMixin<*>): Int = when(binding) {
            // Usable bindings - the order number should be stable.
            is ModuleHostedMixin -> 0
            is InjectConstructorProvisionBindingImpl -> 10
            is AssistedInjectFactoryBindingImpl -> 40
            is ComponentDependencyBindingImpl -> 50
            is ComponentDependencyEntryPointBindingImpl -> 60
            is ComponentInstanceBindingImpl -> 70
            is SubComponentFactoryBindingImpl -> 80
            is InstanceBindingImpl -> 90
            is MapBindingImpl -> 100
            is MultiBindingImpl -> 110
            // Invalid bindings, don't really care for their order - it's UB to use them anyway.
            is MissingBindingImpl -> Int.MAX_VALUE - 1
            is AliasLoopStubBinding -> Int.MAX_VALUE - 2
        }
    }
}

internal sealed interface ComparableByTargetBindingMixin : ComparableBindingMixin<ComparableByTargetBindingMixin> {
    override fun compareTo(other: ComparableByTargetBindingMixin): Int {
        target.compareTo(other.target).let { if (it != 0) return it }
        // Fallback to compare owners in very rare case targets are equal
        return owner.model.type.compareTo(other.owner.model.type)
    }
}

internal abstract class ModuleHostedMixin : BaseBindingMixin, ComparableBindingMixin<ModuleHostedMixin> {
    abstract val impl: ModuleHostedBindingModel

    final override val originModule get() = impl.originModule

    final override val target: NodeModel by lazy(PUBLICATION) {
        when (val target = impl.target) {
            is BindingTargetModel.DirectMultiContribution,
            is BindingTargetModel.FlattenMultiContribution,
            is BindingTargetModel.MappingContribution,
            -> MultiBindingContributionNode(target.node)

            is BindingTargetModel.Plain -> target.node
        }
    }

    private class MultiBindingContributionNode(
        private val underlying: NodeModel,
    ) : NodeModel by underlying {
        override fun getSpecificModel(): Nothing? = null
        override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
            modelClassName = "multi-binding-contributor",
            representation = underlying.toString(childContext = null),
        )

        override val node: NodeModel get() = this
    }

    final override fun compareTo(other: ModuleHostedMixin): Int {
        return impl.function.compareTo(other.impl.function)
    }
}

internal class ProvisionBindingImpl(
    override val impl: ProvidesBindingModel,
    override val owner: BindingGraph,
) : ProvisionBinding, ConditionalBindingMixin, ModuleHostedMixin() {

    override val scopes get() = impl.scopes
    override val provision get() = impl.function
    override val inputs get() = impl.inputs
    override val requiresModuleInstance get() = impl.requiresModuleInstance
    override val variantMatch: VariantMatch by lazy { VariantMatch(impl, owner.variant) }

    override val dependencies by lazy(PUBLICATION) {
        if (conditionScope.isNever) emptySequence() else inputs.asSequence()
    }

    override val nonStaticConditionDependencies by lazy {
        NonStaticConditionDependencies(this@ProvisionBindingImpl)
    }

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = "provision",
        childContext = childContext,
        representation = {
            append(impl.originModule.type)
            append("::")
            append(impl.function.name)
        },
    )

    override val checkDependenciesConditionScope get() = true

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitProvision(this)
    }
}

internal class InjectConstructorProvisionBindingImpl(
    private val impl: InjectConstructorModel,
    override val owner: BindingGraph,
) : ProvisionBinding, ConditionalBindingMixin, ComparableByTargetBindingMixin {
    override val target get() = impl.asNode()
    override val originModule: Nothing? get() = null
    override val scopes: Set<AnnotationLangModel> get() = impl.scopes
    override val provision get() = impl.constructor
    override val inputs: List<NodeDependency> get() = impl.inputs
    override val requiresModuleInstance: Boolean = false
    override val variantMatch: VariantMatch by lazy { VariantMatch(impl, owner.variant) }

    override val checkDependenciesConditionScope: Boolean
        get() = true

    override val dependencies by lazy(PUBLICATION) {
        if (conditionScope.isNever) emptySequence() else impl.inputs.asSequence()
    }

    override val nonStaticConditionDependencies by lazy {
        NonStaticConditionDependencies(this@InjectConstructorProvisionBindingImpl)
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        validator.inline(impl)
    }

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitProvision(this)
    }

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = "inject-constructor",
        representation = { append(impl.constructor.constructee) },
        childContext = childContext,
    )
}

internal class AssistedInjectFactoryBindingImpl(
    override val owner: BindingGraph,
    override val model: AssistedInjectFactoryModel,
) : AssistedInjectFactoryBinding, BindingMixin, ComparableByTargetBindingMixin {
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

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = "assisted-factory",
        childContext = childContext,
        representation = {
            append(model.type)
            append("::")
            if (model.factoryMethod != null) {
                append(model.factoryMethod!!.name)
                append("(): ")
                if (model.assistedInjectConstructor != null) {
                    append(model.assistedInjectConstructor!!.constructee)
                } else {
                    appendRichString {
                        color = TextColor.Red
                        append("<invalid-target>")
                    }
                }
            } else {
                appendRichString {
                    color = TextColor.Red
                    append("<missing-factory-method>")
                }
            }
        },
    )
}

internal class SyntheticAliasBindingImpl(
    override val source: NodeModel,
    override val target: NodeModel,
    override val owner: BindingGraph,
) : AliasBinding {
    private val sourceBinding by lazy(PUBLICATION) { owner.resolveBindingRaw(source) }

    override val originModule: ModuleModel? get() = null
    override fun <R> accept(visitor: BaseBinding.Visitor<R>): R {
        return visitor.visitAlias(this)
    }

    override fun validate(validator: Validator) {
        validator.inline(owner.resolveBindingRaw(source))
    }

    override fun toString(childContext: MayBeInvalid?): CharSequence {
        // Pass-through
        return sourceBinding.toString(childContext)
    }

    override fun compareTo(other: BaseBinding): Int {
        // Pass-through
        return sourceBinding.compareTo(other)
    }
}

internal class AliasBindingImpl(
    override val impl: BindsBindingModel,
    override val owner: BindingGraph,
) : AliasBinding, ModuleHostedMixin() {
    init {
        assert(impl.sources.count() == 1) {
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

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = "alias",
        childContext = childContext,
        representation = { append(impl.originModule.type).append("::").append(impl.function.name) },
        ellipsisStatistics = { _, dependencies ->
            if (dependencies.isNotEmpty()) append(dependencies.first())  // Always include alias source
        },
    )

    override fun validate(validator: Validator) {
        validator.child(owner.resolveBindingRaw(source))
        if (impl.scopes.isNotEmpty()) {
            validator.reportMandatoryWarning(Strings.Warnings.scopeRebindIsForbidden()) {
                addNote(Strings.Notes.infoOnScopeRebind())
            }
        }
    }

    override fun <R> accept(visitor: BaseBinding.Visitor<R>): R {
        return visitor.visitAlias(this)
    }
}

internal class AlternativesBindingImpl(
    override val impl: BindsBindingModel,
    override val owner: BindingGraph,
) : AlternativesBinding, BindingMixin, ModuleHostedMixin() {
    override val scopes get() = impl.scopes
    override val alternatives get() = impl.sources

    override val conditionScope: ConditionScope by lazy(PUBLICATION) {
        alternatives.fold(ConditionScope.NeverScoped as ConditionScope) { acc, alternative ->
            val binding = owner.resolveBinding(alternative)
            acc or binding.conditionScope
        }
    }

    override val dependencies get() = alternatives

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = "alias-with-alternatives",
        childContext = childContext,
        representation = { append(impl.originModule.type).append("::").append(impl.function.name) },
    )

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitAlternatives(this)
    }

    // TODO: issue warnings about unreachable alternatives
}

internal class ComponentDependencyEntryPointBindingImpl(
    override val owner: BindingGraph,
    override val dependency: ComponentDependencyModel,
    override val getter: FunctionLangModel,
    override val target: NodeModel,
) : ComponentDependencyEntryPointBinding, BindingMixin, ComparableBindingMixin<ComponentDependencyEntryPointBindingImpl> {

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitComponentDependencyEntryPoint(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-dependency-getter",
        representation = getter,
    )

    override fun compareTo(other: ComponentDependencyEntryPointBindingImpl): Int {
        return getter.compareTo(other.getter)
    }
}

internal class ComponentInstanceBindingImpl(
    graph: BindingGraph,
) : ComponentInstanceBinding, BindingMixin, ComparableByTargetBindingMixin {
    override val owner: BindingGraph = graph
    override val target get() = owner.model.asNode()

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitComponentInstance(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-instance",
        representation = owner,
    )
}

internal class SubComponentFactoryBindingImpl(
    override val owner: BindingGraph,
    private val factory: ComponentFactoryModel,
) : SubComponentFactoryBinding, ConditionalBindingMixin, ComparableByTargetBindingMixin {
    override val target: NodeModel
        get() = factory.asNode()

    override val targetGraph: BindingGraph by lazy {
        val targetComponent = factory.createdComponent
        checkNotNull(owner.children.find { it.model == targetComponent }) {
            "Not reached: $this: Can't find child component $targetComponent among $owner's children."
        }
    }

    override val dependencies by lazy(PUBLICATION) {
        if (conditionScope.isNever) emptySequence()
        else targetGraph.usedParents.map { it.model.asNode() }.asSequence()
    }

    override val variantMatch: VariantMatch by lazy {
        VariantMatch(factory.createdComponent, owner.variant)
    }

    override val nonStaticConditionDependencies by lazy {
        NonStaticConditionDependencies(this@SubComponentFactoryBindingImpl)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "child-component-factory",
        representation = factory,
    )

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitSubComponentFactory(this)
    }
}

internal class MultiBindingImpl(
    override val owner: BindingGraph,
    override val target: NodeModel,
    override val upstream: MultiBindingImpl?,
    override val targetForDownstream: NodeModel,
    override val kind: CollectionTargetKind,
    contributions: Map<Contribution, ContributionType>,
) : MultiBinding, BindingMixin, ComparableBindingMixin<MultiBindingImpl> {
    private val _contributions = contributions

    data class Contribution(
        val contributionDependency: NodeModel,
        val origin: ModuleHostedBindingModel,
    ) : Comparable<Contribution> {
        override fun compareTo(other: Contribution): Int {
            return origin.function.compareTo(other.origin.function)
        }
    }

    override val contributions: Map<NodeModel, ContributionType> by lazy {
        when (kind) {
            CollectionTargetKind.List -> {
                // Resolve aliases as multi-bindings often work with @Binds
                val resolved = _contributions.mapKeys { (contribution, _) ->
                    owner.resolveBinding(contribution.contributionDependency).target
                }
                topologicalSort(
                    nodes = resolved.keys,
                    inside = owner,
                ).associateWith(resolved::getValue)
            }

            CollectionTargetKind.Set -> {
                _contributions.mapKeys { it.key.contributionDependency }
            }
        }
    }

    override val dependencies get() = extensibleAwareDependencies(
        _contributions.keys.asSequence().map { it.contributionDependency })

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = when(kind) {
            CollectionTargetKind.List -> "list-binding"
            CollectionTargetKind.Set -> "set-binding"
        },
        childContext = childContext,
        representation = { append("List ") },
        childContextTransform = { dependency ->
            when (dependency.node) {
                upstream?.targetForDownstream -> "<inherited from parent component>"
                else -> dependency
            }
        },
        ellipsisStatistics = {_, dependencies ->
            var elements = 0
            var collections = 0
            var mentionUpstream = false
            for (dependency in dependencies) when(contributions[dependency.node]) {
                ContributionType.Element -> elements++
                ContributionType.Collection -> collections++
                null -> mentionUpstream = (dependency.node == upstream?.targetForDownstream)
            }
            sequenceOf(
                when(elements) {
                    0 -> null
                    1 -> "1 element"
                    else -> "$elements elements"
                },
                when(collections) {
                    0 -> null
                    1 -> "1 collection"
                    else -> "$collections collections"
                },
                if (mentionUpstream) "upstream" else null,
            ).filterNotNull().joinTo(this, separator = " + ")
        },
        openBracket = " { ",
        closingBracket = buildRichString {
            append(" } ")
            appendRichString {
                color = TextColor.Gray
                append("assembled in ")
            }
            append(owner)
        },
    )

    override fun compareTo(other: MultiBindingImpl): Int {
        return MapComparator.ofComparable<Contribution, ContributionType>()
            .compare(_contributions, other._contributions)
    }

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitMulti(this)
    }
}

internal class MapBindingImpl(
    override val owner: BindingGraph,
    override val target: NodeModel,
    override val contents: List<Contribution>,
    override val mapKey: TypeLangModel,
    override val mapValue: TypeLangModel,
    override val upstream: MapBindingImpl?,
    override val targetForDownstream: NodeModel,
) : MapBinding, BindingMixin, ComparableBindingMixin<MapBindingImpl> {

    data class Contribution(
        override val keyValue: AnnotationLangModel.Value,
        override val dependency: NodeDependency,
        val origin: ModuleHostedBindingModel,
    ) : MapBinding.Contribution, Comparable<Contribution> {
        override fun compareTo(other: Contribution): Int {
            return origin.function.compareTo(other.origin.function)
        }
    }

    private val allResolvedAndGroupedContents: Map<AnnotationLangModel.Value, List<BaseBinding>> by lazy {
        mergeMultiMapsForDuplicateCheck(
            fromParent = upstream?.allResolvedAndGroupedContents,
            current = contents.groupBy(
                keySelector = { (key, _) -> key },
                // Resolution on `owner` is important here, so do it eagerly
                valueTransform = { it -> owner.resolveBindingRaw(it.dependency.node) },
            ),
        )
    }

    override val dependencies: Sequence<NodeDependency>
        get() = extensibleAwareDependencies(contents.asSequence().map { (_, dependency) -> dependency })

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitMap(this)
    }

    override fun validate(validator: Validator) {
        super.validate(validator)

        for ((key, bindings) in allResolvedAndGroupedContents) {
            if (bindings.size > 1) {
                // Found duplicates
                validator.reportError(Errors.duplicateKeysInMapping(mapType = target, keyValue = key)) {
                    for (binding in bindings) {
                        addNote(Strings.Notes.duplicateKeyInMapBinding(binding = binding))
                    }
                }
            }
        }
    }

    override fun compareTo(other: MapBindingImpl): Int {
        return ListComparator.ofComparable<Contribution>(asSorted = true)
            .compare(contents, other.contents)
    }

    override fun toString(childContext: MayBeInvalid?) = bindingModelRepresentation(
        modelClassName = "map-binding of",
        childContext = childContext,
        representation = {
            append(mapKey)
            appendRichString {
                color = TextColor.Gray
                append(" to ")
            }
            append(mapValue)
        },
        childContextTransform = { context ->
            val key = contents.find { (_, dependency) -> dependency == context }?.keyValue
            if (key != null) {
                "$key -> $mapValue"
            } else if (upstream?.targetForDownstream == context) {
                "<inherited from parent component>"
            } else throw AssertionError()
        },
        ellipsisStatistics = {_,  dependencies ->
            var elements = 0
            var mentionUpstream = false
            for (dependency in dependencies) when(dependency) {
                upstream?.targetForDownstream -> mentionUpstream = true
                else -> elements++
            }
            sequenceOf(
                when(elements) {
                    0 -> null
                    1 -> "1 element"
                    else -> "$elements elements"
                },
                if (mentionUpstream) "upstream" else null,
            ).filterNotNull().joinTo(this, separator = " + ")
        },
        openBracket = " { ",
        closingBracket = buildRichString {
            append(" } ")
            appendRichString {
                color = TextColor.Gray
                append("assembled in ")
            }
            append(owner)
        },
    )
}

internal class ExplicitEmptyBindingImpl(
    override val impl: ModuleHostedBindingModel,
    override val owner: BindingGraph,
) : EmptyBinding, BindingMixin, ModuleHostedMixin() {
    override val conditionScope get() = ConditionScope.NeverScoped

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "explicit-absent by",
        representation = impl,
    )
}

internal class ComponentDependencyBindingImpl(
    override val dependency: ComponentDependencyModel,
    override val owner: BindingGraph,
) : ComponentDependencyBinding, BindingMixin, ComparableByTargetBindingMixin {
    override val target get() = dependency.asNode()

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitComponentDependency(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "component-dependency-instance",
        representation = dependency,
    )
}

internal class InstanceBindingImpl(
    override val target: NodeModel,
    override val owner: BindingGraph,
    private val origin: ComponentFactoryModel.InputModel,
) : InstanceBinding, BindingMixin, ComparableByTargetBindingMixin {

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitInstance(this)
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "bound-instance from",
        representation = origin,
    )
}

internal class SelfDependentInvalidBinding(
    override val impl: ModuleHostedBindingModel,
    override val owner: BindingGraph,
) : EmptyBinding, BindingMixin, ModuleHostedMixin() {
    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        // Always invalid
        validator.reportError(Errors.selfDependentBinding())
    }

    override fun toString(childContext: MayBeInvalid?) = buildRichString {
        color = TextColor.Red
        append("<invalid> ")
        append(impl)
    }
}

internal class AliasLoopStubBinding(
    override val owner: BindingGraph,
    override val target: NodeModel,
    private val aliasLoop: Collection<AliasBinding>,
) : EmptyBinding, BindingMixin, ComparableByTargetBindingMixin {
    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }

    override fun validate(validator: Validator) {
        super.validate(validator)
        validator.reportError(Errors.dependencyLoop(aliasLoop.toList()))
    }

    override fun toString(childContext: MayBeInvalid?) = buildRichString {
        color = TextColor.Red
        append("<alias-loop> ")
        append(aliasLoop.first())
    }
}

internal data class MissingBindingImpl(
    override val target: NodeModel,
    override val owner: BindingGraph,
) : EmptyBinding, BindingMixin, ComparableByTargetBindingMixin {

    override fun validate(validator: Validator) {
        target.getSpecificModel().accept(ModelBasedHint(validator))
        // TODO: implement hint about how to provide a binding
        //  - maybe the same differently qualified binding exists
        //  - binding exists in a sibling component hierarchy path
    }

    override fun <R> accept(visitor: Binding.Visitor<R>): R {
        return visitor.visitEmpty(this)
    }

    override fun toString(childContext: MayBeInvalid?) = buildRichString {
        color = TextColor.Red
        append("<missing>")
    }

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
            val failedInject = InjectConstructorProvisionBindingImpl(
                impl = model,
                owner = owner,
            )
            validator.reportError(Errors.noMatchingScopeForBinding(
                binding = failedInject,
                scopes = model.scopes,
            ))
        }

        override fun visitComponent(model: ComponentModel) = reportMissingBinding {
            addNote(Strings.Notes.suspiciousComponentInstanceInject())
        }

        override fun visitAssistedInjectFactory(model: AssistedInjectFactoryModel): Nothing {
            throw AssertionError("Not reached: assisted inject factory can't be missing")
        }

        override fun visitComponentFactory(model: ComponentFactoryModel) = reportMissingBinding {
            addNote(
                if (!model.createdComponent.isRoot) {
                    Strings.Notes.subcomponentFactoryInjectionHint(
                        factory = model,
                        component = model.createdComponent,
                        owner = owner,
                    )
                } else {
                    Strings.Notes.suspiciousRootComponentFactoryInject(factoryLike = target)
                }
            )
        }
    }
}

private fun ExtensibleBinding<*>.extensibleAwareDependencies(
    baseDependencies: Sequence<NodeDependency>,
): Sequence<NodeDependency> {
    return upstream?.let { baseDependencies + it.targetForDownstream } ?: baseDependencies
}
