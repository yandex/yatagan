package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.component1
import com.yandex.daggerlite.core.component2
import com.yandex.daggerlite.graph.AliasBinding
import com.yandex.daggerlite.graph.BaseBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.ConditionScope
import com.yandex.daggerlite.graph.normalized
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.Validator.ChildValidationKind.Inline
import com.yandex.daggerlite.validation.impl.wrap

internal class BindingGraphImpl(
    override val model: ComponentModel,
    override val parent: BindingGraphImpl? = null,
) : BindingGraph {
    override val variant: Variant = model.variant + parent?.variant

    private val bindings = GraphBindingsFactory(
        modules = model.modules,
        dependencies = model.dependencies,
        factory = model.factory,
        graph = this,
        scope = model.scope,
    )

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
        children = model.modules
            .asSequence()
            .flatMap(ModuleModel::subcomponents)
            .filter { !VariantMatch(it, variant).conditionScope.isNever }
            .distinct()
            .map { BindingGraphImpl(it, parent = this) }
            .toList()

        model.entryPoints.forEach { entryPoint ->
            materializationQueue.add(entryPoint.dependency)
        }
        model.memberInjectors.forEach { membersInjector ->
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

    override fun toString() = buildList<ComponentModel> {
        var current: BindingGraph? = this@BindingGraphImpl
        while (current != null) {
            add(current.model)
            current = current.parent
        }
        reverse()
    }.joinToString(separator = "::")

    override fun validate(validator: Validator) {
        validator.child(model)
        validator.child(bindings, kind = Inline)
        children.forEach(validator::child)

        // Validate every used binding in a graph structure.

        // Reachable via entry-points.
        for ((getter, dependency) in model.entryPoints) {
            validator.child(resolveBinding(dependency.node).wrap("[entry-point] ${getter.name}"))
        }
        // Reachable via members-inject.
        for (memberInjector in model.memberInjectors) {
            for ((member, dependency) in memberInjector.membersToInject) {
                validator.child(resolveBinding(dependency.node)
                    .wrap("[member-to-inject] ${member.name}")
                    .wrap("[injector-fun] ${memberInjector.injector.name}")
                )
            }
        }

        // Validate graph integrity and soundness as a whole

        // TODO: validate no loops in a graph
        // TODO: validate dependency conditions
    }
}

class BindingUsageImpl : BindingGraph.BindingUsage {
    private var _direct: Int = 0
    private var _provider: Int = 0
    private var _lazy: Int = 0
    private var _optional: Int = 0
    private var _optionalLazy: Int = 0
    private var _optionalProvider: Int = 0

    override val direct get() = _direct + _optional
    override val provider get() = _provider + _optionalProvider
    override val lazy get() = _lazy + _optionalLazy
    override val optional get() = _optional + _optionalLazy + _optionalProvider
    override val optionalLazy get() = _optionalLazy
    override val optionalProvider get() = _optionalProvider

    fun accept(dependencyKind: DependencyKind) {
        when (dependencyKind) {
            DependencyKind.Direct -> _direct++
            DependencyKind.Lazy -> _lazy++
            DependencyKind.Provider -> _provider++
            DependencyKind.Optional -> _optional++
            DependencyKind.OptionalLazy -> _optionalLazy++
            DependencyKind.OptionalProvider -> _optionalProvider++
        }.let { /*exhaustive*/ }
    }
}

/**
 * Creates [BindingGraph] instance given the root component.
 */
fun BindingGraph(root: ComponentModel): BindingGraph {
    require(root.isRoot) { "Not reached: can't use non-root component as a root of a binding graph" }
    return BindingGraphImpl(
        model = root,
    )
}
