package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.core.ComponentDependencyInput
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.ModuleInstanceInput
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.Variant
import com.yandex.daggerlite.core.allInputs
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.graph.AliasBinding
import com.yandex.daggerlite.graph.BaseBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.BindingGraph.NodeRequester
import com.yandex.daggerlite.graph.BindingGraph.NodeRequester.BindingRequester
import com.yandex.daggerlite.graph.BindingGraph.NodeRequester.EntryPointRequester
import com.yandex.daggerlite.graph.BindingGraph.NodeRequester.MemberInjectRequester
import com.yandex.daggerlite.graph.ConditionScope
import com.yandex.daggerlite.graph.MissingBindingException
import com.yandex.daggerlite.graph.normalized

private class BindingGraphImpl(
    override val model: ComponentModel,
    override val parent: BindingGraphImpl? = null,
    private val factory: LangModelFactory,
) : BindingGraph {
    override val variant: Variant = model.variant + parent?.variant

    private val resolveHelper = hashMapOf<NodeModel, Binding>()
    override val modules = run {
        val seenModules = hashSetOf<ModuleModel>()
        val moduleQueue = ArrayDeque(model.modules)
        while (moduleQueue.isNotEmpty()) {
            val module = moduleQueue.removeFirst()
            if (!seenModules.add(module)) {
                continue
            }
            moduleQueue += module.includes
        }
        seenModules
    }
    private val allProvidedBindings: MutableMap<NodeModel, BaseBinding?> = buildBindingsSequence(
        graph = this,
        langModelFactory = factory,
    ).groupBy(BaseBinding::target).mapValuesTo(mutableMapOf()) { (target, bindings) ->
        if (bindings.size != 1) {
            val distinct = bindings.toSet()
            check(distinct.size == 1) {
                "$this: Multiple bindings for $target: ${
                    bindings.joinToString(separator = ",\n") {
                        "$it FROM ${it.originModule}"
                    }
                }"
            }
        }
        bindings.first()
    }

    override val localBindings = mutableMapOf<Binding, BindingUsageImpl>()
    override val localConditionLiterals = mutableSetOf<ConditionScope.Literal>()
    override val missingBindings: Map<NodeModel, List<NodeRequester>>
    override val usedParents = mutableSetOf<BindingGraph>()
    override val children: Collection<BindingGraphImpl>

    override fun resolveBinding(node: NodeModel): Binding {
        return resolveHelper[node] ?: parent?.resolveBinding(node) ?: throw MissingBindingException(node)
    }

    // MAYBE: write algorithm in such a way that this is local variable.
    private val materializationQueue: MutableList<Pair<NodeDependency, NodeRequester>> = ArrayDeque()

    init {
        // Pre-validate factory inputs
        validateFactoryInputs()

        // Build children
        children = modules
            .asSequence()
            .flatMap(ModuleModel::subcomponents)
            .filter { it.conditionScopeFor(variant) != null }
            .distinct()
            .map { BindingGraphImpl(it, parent = this, factory = factory) }
            .toList()

        val missing = hashMapOf<NodeModel, MutableList<NodeRequester>>()

        model.entryPoints.forEach { entryPoint ->
            materializationQueue.add(entryPoint.dependency to EntryPointRequester(entryPoint))
        }
        model.memberInjectors.forEach { membersInjector ->
            membersInjector.membersToInject.values.forEach { injectDependency ->
                materializationQueue.add(injectDependency to MemberInjectRequester(membersInjector))
            }
        }

        val seenBindings = hashSetOf<Binding>()
        while (materializationQueue.isNotEmpty()) {
            val (dependency, requester) = materializationQueue.removeFirst()
            val binding = materialize(dependency, requester)
            if (binding == null) {
                missing.getOrPut(dependency.node, ::mutableListOf) += requester
                continue
            }
            if (binding.owner == this) {
                if (!seenBindings.add(binding)) {
                    continue
                }
                materializationQueue += binding.dependencies().map { it to BindingRequester(binding) }
            }
        }
        missingBindings = missing

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

        // No longer required
        allProvidedBindings.clear()
    }

    private fun materialize(dependency: NodeDependency, requester: NodeRequester): Binding? {
        return materializeLocal(dependency, requester) ?: materializeInParents(dependency, requester)
    }

    private fun materializeLocal(dependency: NodeDependency, requester: NodeRequester): Binding? {
        fun materializeAlias(maybeAlias: BaseBinding?): Binding? {
            var binding: BaseBinding? = maybeAlias
            while (true) {
                binding = when (binding) {
                    is AliasBinding -> materialize(NodeDependency(binding.source), requester)
                    is Binding -> return binding
                    null -> return null
                }
            }
        }

        val (node, kind) = dependency
        val binding = allProvidedBindings.getOrPut(node, fun(): BaseBinding? {
            if (node.qualifier != null) {
                return null
            }
            val inject = node.implicitBinding ?: return null

            if (inject.scope != null && inject.scope != model.scope) {
                return null
            }

            return inject.conditionScopeFor(variant)?.let { conditionScope ->
                InjectConstructorProvisionBindingImpl(
                    impl = inject,
                    owner = this@BindingGraphImpl,
                    conditionScope = conditionScope,
                )
            } ?: EmptyBindingImpl(
                owner = this@BindingGraphImpl,
                target = node,
                reason = "conditional ruled out"
            )
        })
        val nonAlias = materializeAlias(binding) ?: return null

        resolveHelper[node] = nonAlias
        if (nonAlias.owner == this) {
            // materializeAlias may have yielded non-local binding, so check.
            localBindings.getOrPut(nonAlias, ::BindingUsageImpl).accept(kind)
        }
        return nonAlias
    }

    private fun materializeInParents(dependency: NodeDependency, requester: NodeRequester): Binding? {
        if (parent == null) {
            return null
        }
        val binding = parent.materializeLocal(dependency, requester)
        if (binding != null) {
            // The binding is requested from a parent, so add parent to dependencies.
            usedParents += parent
            parent.materializationQueue += dependency to requester
            return binding
        }
        return parent.materializeInParents(dependency, requester)?.also {
            usedParents += it.owner
        }
    }

    private fun validateFactoryInputs() {
        val factory = model.factory ?: return

        val providedComponents = factory.allInputs
            .filterIsInstance<ComponentDependencyInput>()
            .map(ComponentDependencyInput::component).toSet()
        check(model.dependencies == providedComponents) {
            val missing = model.dependencies - providedComponents
            if (missing.isNotEmpty()) "Missing dependency components in $factory: $missing"
            else "Unneeded components in $factory: ${providedComponents - model.dependencies}"
        }

        val providedModules = factory.allInputs
            .filterIsInstance<ModuleInstanceInput>()
            .map(ModuleInstanceInput::module).toSet()
        val allModulesRequiresInstance = modules.asSequence().filter(ModuleModel::requiresInstance).toMutableSet()

        val missing = (allModulesRequiresInstance - providedModules).filter { !it.isTriviallyConstructable }
        check(missing.isEmpty()) {
            "Missing modules in $factory: $missing"
        }
        val unneeded = providedModules - allModulesRequiresInstance
        check(unneeded.isEmpty()) {
            "Unneeded modules in $factory: $unneeded"
        }
    }

    override fun toString(): String {
        return "BindingGraph[$model]"
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
fun BindingGraph(root: ComponentModel, modelFactory: LangModelFactory): BindingGraph {
    require(root.isRoot) { "can't use non-root component as a root of a binding graph" }
    return BindingGraphImpl(
        model = root,
        factory = modelFactory,
    )
}
