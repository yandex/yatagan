package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.graph.AliasBinding
import com.yandex.daggerlite.graph.BaseBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.ConditionScope
import com.yandex.daggerlite.graph.normalized
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.Validator.ChildValidationKind.Inline
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.reportError

internal class BindingGraphImpl(
    private val component: ComponentModel,
    override val parent: BindingGraphImpl? = null,
    override val conditionScope: ConditionScope,
) : BindingGraph {
    override val model: ComponentModel
        get() = component

    override val isRoot: Boolean
        get() = component.isRoot

    override val variant: Variant = component.variant + parent?.variant

    override val scope: AnnotationLangModel?
        get() = component.scope

    override val creator: ComponentFactoryModel?
        get() = component.factory

    override val modules: Collection<ModuleModel>
        get() = component.modules

    override val dependencies: Collection<ComponentDependencyModel>
        get() = component.dependencies

    override val entryPoints = component.entryPoints.map { GraphEntryPointImpl(owner = this, impl = it) }

    override val memberInjectors = component.memberInjectors.map { GraphMemberInjectorImpl(owner = this, impl = it) }

    private val bindings: GraphBindingsFactory = GraphBindingsFactory(
        graph = this,
        parent = parent?.bindings,
    )

    override val localBindings = mutableMapOf<Binding, BindingUsageImpl>()
    override val localConditionLiterals = mutableSetOf<ConditionScope.Literal>()
    override val usedParents = mutableSetOf<BindingGraph>()
    override val children: Collection<BindingGraphImpl>

    override fun resolveBinding(node: NodeModel): Binding {
        class AliasResolveVisitor : BaseBinding.Visitor<Binding> {
            override fun visitAlias(alias: AliasBinding) = resolveRaw(alias.source).accept(this)
            override fun visitBinding(binding: Binding) = binding
        }
        return resolveRaw(node).accept(AliasResolveVisitor())
    }

    internal fun resolveRaw(node: NodeModel): BaseBinding {
        return bindings.getBindingFor(node)
            ?: parent?.resolveRaw(node)
            ?: throw AssertionError("Not reached: missing binding for $node")
    }

    // MAYBE: write algorithm in such a way that this is local variable.
    private val materializationQueue: MutableList<NodeDependency> = ArrayDeque()

    init {
        // Build children
        children = if (parents().any { it.component == component }) /*loop guard*/ emptyList() else modules
            .asSequence()
            .flatMap(ModuleModel::subcomponents)
            .distinct()
            .map { it to VariantMatch(it, variant).conditionScope }
            .filter { (_, conditionScope) -> !conditionScope.isNever }
            .map { (childComponent, childConditionScope) ->
                BindingGraphImpl(
                    component = childComponent,
                    parent = this,
                    conditionScope = conditionScope and childConditionScope
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
                    val carryDependency = dependency.copy(node = alias.source)
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
                materializationQueue += nonAlias.dependencies()
            }
        }

        // Add all condition literals from all local bindings.
        for (binding in localBindings.keys) for (clause in binding.conditionScope.expression) for (literal in clause) {
            localConditionLiterals += literal.normalized()
        }
        // Remove all local condition literals from every child (in-depth).
        val graphQueue = ArrayDeque(children)
        while (graphQueue.isNotEmpty()) {
            val child = graphQueue.removeFirst()
            if (child.localConditionLiterals.removeAll(localConditionLiterals)) {
                // This will never be seen by materialization and that's okay, because no bindings are required here.
                child.usedParents += this
            }
            graphQueue += child.children
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

    override fun toString() = component.toString()

    override fun validate(validator: Validator) {
        validator.child(component, kind = Inline)
        validator.child(bindings, kind = Inline)
        validator.child(variant)
        children.forEach(validator::child)

        // Validate every used binding in a graph structure.

        // Reachable via entry-points.
        entryPoints.forEach(validator::child)
        // Reachable via members-inject.
        memberInjectors.forEach(validator::child)

        // Check component root status
        if (parent != null && isRoot) {
            validator.reportError(Strings.Errors.`root component can not be a subcomponent`())
        }

        // Check for duplicate scopes
        scope?.let { scope ->
            parents().find { parent -> parent.scope == scope }?.let { withDuplicateScope ->
                validator.reportError(Strings.Errors.`duplicate component scope`(scope)) {
                    addNote(Strings.Notes.`duplicate scope component`(component = withDuplicateScope))
                    addNote(Strings.Notes.`duplicate scope component`(component = this@BindingGraphImpl))
                }
            }
        }

        // Report hierarchy loops
        if (parents().find { parent -> parent.component == component } != null) {
            validator.reportError(Strings.Errors.`component hierarchy loop`())
        }

        validateNoLoops(this, validator)
    }

    private fun parents() = sequence<BindingGraphImpl> {
        var current = parent
        while (current != null) {
            yield(current)
            current = current.parent
        }
    }
}
