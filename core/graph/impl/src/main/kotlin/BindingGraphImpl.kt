package com.yandex.daggerlite.core.graph.impl

import com.yandex.daggerlite.core.graph.AliasBinding
import com.yandex.daggerlite.core.graph.AssistedInjectFactoryBinding
import com.yandex.daggerlite.core.graph.BaseBinding
import com.yandex.daggerlite.core.graph.Binding
import com.yandex.daggerlite.core.graph.BindingGraph
import com.yandex.daggerlite.core.graph.BindingGraph.LiteralUsage
import com.yandex.daggerlite.core.graph.BindingVisitorAdapter
import com.yandex.daggerlite.core.graph.childrenSequence
import com.yandex.daggerlite.core.graph.normalized
import com.yandex.daggerlite.core.graph.parentsSequence
import com.yandex.daggerlite.core.model.AssistedInjectFactoryModel
import com.yandex.daggerlite.core.model.ComponentDependencyModel
import com.yandex.daggerlite.core.model.ComponentFactoryModel
import com.yandex.daggerlite.core.model.ComponentModel
import com.yandex.daggerlite.core.model.ConditionModel
import com.yandex.daggerlite.core.model.ConditionScope
import com.yandex.daggerlite.core.model.ModuleModel
import com.yandex.daggerlite.core.model.NodeDependency
import com.yandex.daggerlite.core.model.NodeModel
import com.yandex.daggerlite.core.model.Variant
import com.yandex.daggerlite.core.model.isNever
import com.yandex.daggerlite.lang.Annotation
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError

internal class BindingGraphImpl(
    private val component: ComponentModel,
    override val parent: BindingGraphImpl? = null,
    override val conditionScope: ConditionScope = ConditionScope.Unscoped,
) : BindingGraph, ExtensibleImpl() {
    override val model: ComponentModel
        get() = component

    override val isRoot: Boolean
        get() = component.isRoot

    override val variant: Variant = component.variant + parent?.variant

    override val scopes: Set<Annotation>
        get() = component.scopes

    override val creator: ComponentFactoryModel?
        get() = component.factory

    override val modules: Collection<ModuleModel>
        get() = component.modules

    override val dependencies: Collection<ComponentDependencyModel>
        get() = component.dependencies

    override val entryPoints = component.entryPoints.map { GraphEntryPointImpl(graph = this, impl = it) }

    override val memberInjectors = component.memberInjectors.map { GraphMemberInjectorImpl(owner = this, impl = it) }

    override val requiresSynchronizedAccess: Boolean
        get() = component.requiresSynchronizedAccess

    private val bindings: GraphBindingsManager = GraphBindingsManager(
        graph = this,
    )

    override val localBindings = mutableMapOf<Binding, BindingUsageImpl>()
    override val localConditionLiterals = mutableMapOf<ConditionModel, LiteralUsage>()
    override val localAssistedInjectFactories = mutableSetOf<AssistedInjectFactoryModel>()
    override val usedParents = mutableSetOf<BindingGraph>()
    override val children: Collection<BindingGraphImpl>

    override fun resolveBinding(node: NodeModel): Binding {
        class AliasResolveVisitor : BaseBinding.Visitor<Binding> {
            override fun visitAlias(alias: AliasBinding) = resolveBindingRaw(alias.source).accept(this)
            override fun visitBinding(binding: Binding) = binding
        }
        return resolveBindingRaw(node).accept(AliasResolveVisitor())
    }

    override fun resolveBindingRaw(node: NodeModel): BaseBinding {
        return bindings.getBindingFor(node)
            ?: parent?.resolveBindingRaw(node)
            ?: throw IllegalStateException("Not reached: missing binding for $node")
    }

    // MAYBE: write algorithm in such a way that this is local variable.
    private val materializationQueue: MutableList<NodeDependency> = ArrayDeque()

    init {
        // Build children
        children = if (parentsSequence().any { it.model == model }) /*loop guard*/ emptyList() else modules
            .asSequence()
            .flatMap(ModuleModel::subcomponents)
            .distinct()
            .map { it to VariantMatch(it, variant).conditionScope }
            .filter { (_, conditionScope) -> !conditionScope.isNever }
            .map { (childComponent, childConditionScope) ->
                BindingGraphImpl(
                    component = childComponent,
                    parent = this,
                    conditionScope = conditionScope and childConditionScope,
                )
            }
            .toList()

        entryPoints.forEach { entryPoint ->
            materializationQueue.add(entryPoint.dependency)
        }
        memberInjectors.forEach { membersInjector ->
            membersInjector.membersToInject.values.forEach { injectDependency ->
                materializationQueue.add(injectDependency)
            }
        }

        val seenBindings = hashSetOf<Binding>()
        while (materializationQueue.isNotEmpty()) {
            val dependency = materializationQueue.removeFirst()
            val binding: BaseBinding = materialize(dependency)
            class AliasMaterializeVisitor : BaseBinding.Visitor<Binding> {
                var aliases = mutableSetOf<AliasBinding>()
                override fun visitAlias(alias: AliasBinding): Binding {
                    val carryDependency = dependency.copyDependency(node = alias.source)
                    if (!aliases.add(alias)) {
                        // Alias loop detected, bail out.
                        return bindings.materializeAliasLoop(node = dependency.node, chain = aliases)
                    }
                    return materialize(carryDependency).accept(this)
                }
                override fun visitBinding(binding: Binding) = binding
            }
            // MAYBE: employ local alias resolution cache
            val nonAlias = binding.accept(AliasMaterializeVisitor())
            if (nonAlias.owner == this) {
                localBindings.getOrPut(nonAlias, ::BindingUsageImpl).accept(dependency.kind)
            }
            if (binding.owner == this) {
                if (!seenBindings.add(nonAlias)) {
                    continue
                }
                materializationQueue += nonAlias.dependencies

                // Add all non-static condition receivers
                materializationQueue += nonAlias.nonStaticConditionProviders
            }
        }

        // Add all local assisted binding factories.
        for (binding in localBindings.keys) {
            binding.accept(object : BindingVisitorAdapter<Unit>() {
                override fun visitDefault() = Unit
                override fun visitAssistedInjectFactory(binding: AssistedInjectFactoryBinding) {
                    localAssistedInjectFactories += binding.model
                }
            })
        }

        // Add all condition literals from all local bindings.
        for (binding in localBindings.keys) {
            var isEager = true
            for (clause in binding.conditionScope.expression) for (literal in clause) {
                val normalized = literal.normalized()
                if (isEager) {
                    localConditionLiterals[normalized] = when {
                        normalized.requiresInstance -> LiteralUsage.Lazy  // Always lazy
                        else -> LiteralUsage.Eager
                    }
                    isEager = false
                } else {
                    if (normalized !in localConditionLiterals) {
                        localConditionLiterals[normalized] = LiteralUsage.Lazy
                    }
                }
            }
        }
        // Remove all local condition literals/assisted inject factories from every child (in-depth).
        for (child in childrenSequence(includeThis = false)) {
            child as BindingGraphImpl
            val usesConditions = child.localConditionLiterals.keys.removeAll(localConditionLiterals.keys)
            val usesAssistedInjectFactories = child.localAssistedInjectFactories.removeAll(localAssistedInjectFactories)
            if (usesConditions || usesAssistedInjectFactories) {
                // This will never be seen by materialization and that's okay, because no bindings are required here.
                child.usedParents += this
            }
        }
    }

    private fun materialize(dependency: NodeDependency): BaseBinding {
        return materializeLocal(dependency)
            ?: materializeInParents(dependency, BindingGraphImpl::materializeLocal)
            ?: materializeImplicit(dependency)
            ?: materializeInParents(dependency, BindingGraphImpl::materializeImplicit)
            ?: materializeMissing(dependency)
    }

    private fun materializeMissing(dependency: NodeDependency): Binding {
        return bindings.materializeMissing(dependency.node)
    }

    private fun materializeLocal(dependency: NodeDependency): BaseBinding? {
        return bindings.getExplicitBindingFor(dependency.node)
    }

    private fun materializeImplicit(dependency: NodeDependency): Binding? {
        return bindings.materializeImplicitBindingFor(dependency.node)
    }

    private fun materializeInParents(
        dependency: NodeDependency,
        materializeFunction: BindingGraphImpl.(NodeDependency) -> BaseBinding?,
    ): BaseBinding? {
        if (parent == null) return null
        val binding = parent.materializeFunction(dependency)
        if (binding != null) {
            // The binding is requested from a parent, so add parent to dependencies.
            usedParents += parent
            parent.materializationQueue += dependency
            return binding
        }
        return parent.materializeInParents(dependency, materializeFunction)?.also {
            usedParents += it.owner
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "graph for",
        representation = component,
    )

    override fun validate(validator: Validator) {
        validator.inline(component)
        validator.inline(bindings)
        validator.child(variant)
        children.forEach(validator::child)

        // Validate every used binding in a graph structure.

        // Reachable via entry-points.
        entryPoints.forEach(validator::child)
        // Reachable via members-inject.
        memberInjectors.forEach(validator::child)

        // Check component root status
        if (parent != null && isRoot) {
            validator.reportError(Strings.Errors.rootAsChild())
        }

        val parents = parentsSequence()

        // Check MT status is set explicitly
        if (component.requiresSynchronizedAccess) {
            parents.filter {
                !(it as BindingGraphImpl).component.requiresSynchronizedAccess
            }.forEach { parent ->
                validator.reportError(Strings.Errors.multiThreadStatusMismatch(parent = parent))
            }
        }

        // Check for duplicate scopes
        scopes.forEach { scope ->
            parents.find { parent -> scope in parent.scopes }?.let { withDuplicateScope ->
                validator.reportError(Strings.Errors.duplicateComponentScope(scope)) {
                    addNote(Strings.Notes.duplicateScopeComponent(component = withDuplicateScope))
                    addNote(Strings.Notes.duplicateScopeComponent(component = this@BindingGraphImpl))
                }
            }
        }

        // Report hierarchy loops
        if (parents.find { parent -> parent.model == model } != null) {
            validator.reportError(Strings.Errors.componentLoop())
        }

        validateNoLoops(this, validator)
        validateAnnotationsRetention(this, validator)
    }
}
