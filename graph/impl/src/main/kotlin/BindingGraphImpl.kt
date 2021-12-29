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

    private val bindings = GraphBindingsFactory(graph = this)

    override val localBindings = mutableMapOf<Binding, BindingUsageImpl>()
    override val localConditionLiterals = mutableSetOf<ConditionScope.Literal>()
    override val usedParents = mutableSetOf<BindingGraph>()
    override val children: Collection<BindingGraphImpl>

    override fun resolveBinding(node: NodeModel): Binding {
        var source: NodeModel = node
        do when (val maybeAlias = resolveRaw(source)) {
            is AliasBinding -> source = maybeAlias.source
            is Binding -> return maybeAlias
        } while (true)
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
        children = modules
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
            val binding = materialize(dependency)
            if (binding.owner == this) {
                if (!seenBindings.add(binding)) {
                    continue
                }
                materializationQueue += binding.dependencies()
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

    private fun materialize(dependency: NodeDependency): Binding {
        return materializeLocal(dependency) ?: materializeInParents(dependency) ?: materializeMissing(dependency)
    }

    private fun materializeMissing(dependency: NodeDependency): Binding {
        val (node, kind) = dependency
        return bindings.materializeMissing(node).also {
            localBindings.getOrPut(it, ::BindingUsageImpl).accept(kind)
        }
    }

    private fun materializeLocal(dependency: NodeDependency): Binding? {
        class MaterializeAliasVisitor : BaseBinding.Visitor<Binding> {
            override fun visitAlias(alias: AliasBinding): Binding {
                return materialize(dependency.copy(node = alias.source))
            }

            override fun visitBinding(binding: Binding): Binding {
                return binding
            }
        }

        val (node, kind) = dependency
        val binding = bindings.materializeBindingFor(node) ?: return null
        val nonAlias = binding.accept(MaterializeAliasVisitor())

        if (nonAlias.owner == this) {
            // materializeAlias may have yielded non-local binding, so check.
            localBindings.getOrPut(nonAlias, ::BindingUsageImpl).accept(kind)
        }
        return nonAlias
    }

    private fun materializeInParents(dependency: NodeDependency): Binding? {
        if (parent == null) {
            return null
        }
        val binding = parent.materializeLocal(dependency)
        if (binding != null) {
            // The binding is requested from a parent, so add parent to dependencies.
            usedParents += parent
            parent.materializationQueue += dependency
            return binding
        }
        return parent.materializeInParents(dependency)?.also {
            usedParents += it.owner
        }
    }

    override fun toString() = model.toString()

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

        if (parent != null && isRoot) {
            validator.reportError(Strings.Errors.`root component can not be a subcomponent`())
        }

        scope?.let { scope ->
            parents().find { parent -> parent.scope == scope }?.let { withDuplicateScope ->
                validator.reportError(Strings.Errors.`duplicate component scope`(scope)) {
                    addNote("In component $withDuplicateScope")
                    addNote("In component ${this@BindingGraphImpl}")
                }
            }
        }

        if (parents().find { parent -> parent.component == component } != null) {
            validator.reportError(Strings.Errors.`component hierarchy loop`())
        }

        // Validate graph integrity and soundness as a whole
        // TODO: validate no loops in a graph
    }

    private fun parents() = sequence<BindingGraphImpl> {
        var current = parent
        while (current != null) {
            yield(current)
            current = current.parent
        }
    }
}
