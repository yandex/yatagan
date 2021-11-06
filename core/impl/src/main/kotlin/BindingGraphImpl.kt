package com.yandex.daggerlite.core.impl

import com.yandex.daggerlite.BootstrapList
import com.yandex.daggerlite.core.AliasBinding
import com.yandex.daggerlite.core.BaseBinding
import com.yandex.daggerlite.core.Binding
import com.yandex.daggerlite.core.BindingGraph
import com.yandex.daggerlite.core.BootstrapInterfaceModel
import com.yandex.daggerlite.core.ComponentDependencyInput
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ConditionScope
import com.yandex.daggerlite.core.EmptyBinding
import com.yandex.daggerlite.core.InstanceInput
import com.yandex.daggerlite.core.MissingBindingException
import com.yandex.daggerlite.core.ModuleInstanceInput
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.core.lang.getAnnotation


private class BindingGraphImpl(
    override val model: ComponentModel,
    private val factory: LangModelFactory,
    private val parent: BindingGraphImpl? = null,
) : BindingGraph {
    private val resolveHelper = hashMapOf<NodeModel, Binding>()
    private val allProvidedBindings: MutableMap<NodeModel, BaseBinding?> = sequence {
        // Gather bindings from modules
        val seenSubcomponents = hashSetOf<ComponentModel>()
        val seenModules = hashSetOf<ModuleModel>()
        val moduleQueue = ArrayDeque(model.modules)
        val bootstrapSets = HashMap<BootstrapInterfaceModel, MutableSet<NodeModel>>()
        while (moduleQueue.isNotEmpty()) {
            val module = moduleQueue.removeFirst()
            if (!seenModules.add(module)) {
                continue
            }
            // All bindings from installed modules
            yieldAll(module.bindings(forGraph = this@BindingGraphImpl))
            // Subcomponent factories (distinct).
            for (subcomponent: ComponentModel in module.subcomponents) {
                if (seenSubcomponents.add(subcomponent)) {
                    // MAYBE: Factory is actually required.
                    subcomponent.factory?.let { factory: ComponentFactoryModel ->
                        factory.createdComponent.conditionScope(forVariant = model.variant)?.let { conditionScope ->
                            yield(SubComponentFactoryBindingImpl(
                                owner = this@BindingGraphImpl,
                                factory = factory,
                                conditionScope = conditionScope,
                            ))
                        } ?: yield(EmptyBindingImpl(
                            owner = this@BindingGraphImpl,
                            target = factory.asNode(),
                        ))
                    }
                }
            }
            // Handle bootstrap lists
            for (nodeModel: NodeModel in module.bootstrap) {
                for (bootstrapInterface: BootstrapInterfaceModel in nodeModel.bootstrapInterfaces) {
                    bootstrapSets.getOrPut(bootstrapInterface, ::linkedSetOf) += nodeModel
                }
            }

            moduleQueue += module.includes
        }
        // Gather bindings from factory
        model.factory?.let { factory: ComponentFactoryModel ->
            for (input: ComponentFactoryModel.Input in factory.inputs) when (input) {
                is ComponentDependencyInput -> {
                    // Binding for the dependency component itself.
                    yield(input.createBinding(forGraph = this@BindingGraphImpl))
                    // Bindings backed by the component entry-points.
                    for (entryPoint: ComponentModel.EntryPoint in input.component.entryPoints)
                        if (entryPoint.dependency.kind == NodeModel.Dependency.Kind.Direct)
                            yield(ComponentDependencyEntryPointBindingImpl(
                                owner = this@BindingGraphImpl,
                                entryPoint = entryPoint,
                                input = input,
                            ))
                }
                // Instance binding
                is InstanceInput -> yield(input.createBinding(forGraph = this@BindingGraphImpl))
                is ModuleInstanceInput -> {/*no binding for module*/
                }
            }.let { /*exhaustive*/ }
        }
        // Bootstrap lists
        for ((bootstrap: BootstrapInterfaceModel, nodes: Set<NodeModel>) in bootstrapSets) {
            yield(BootstrapListBindingImpl(
                owner = this@BindingGraphImpl,
                target = NodeModelImpl(
                    type = factory.getListType(bootstrap.impl),
                    qualifier = factory.getAnnotation<BootstrapList>(),
                ),
                inputs = nodes,
            ))
        }

        // This component binding
        yield(ComponentInstanceBindingImpl(graph = this@BindingGraphImpl))
    }.associateByTo(mutableMapOf(), BaseBinding::target)

    override val localBindings = mutableMapOf<Binding, BindingUsageImpl>()
    override val localConditionLiterals = mutableSetOf<ConditionScope.Literal>()
    override val missingBindings: Set<NodeModel>
    override val usedParents = mutableSetOf<BindingGraph>()
    override val children: Collection<BindingGraphImpl>

    override fun resolveBinding(node: NodeModel): Binding {
        return resolveHelper[node] ?: parent?.resolveBinding(node) ?: throw MissingBindingException(node)
    }

    // MAYBE: write algorithm in such a way that this is local variable.
    private val materializationQueue: MutableList<NodeModel.Dependency> = ArrayDeque()

    init {
        // Build children
        children = model.modules
            .asSequence()
            .flatMap(ModuleModel::subcomponents)
            .filter { it.conditionScope(model.variant) != null }
            .distinct()
            .map { BindingGraphImpl(it, parent = this, factory = factory) }
            .toList()

        val missing = hashSetOf<NodeModel>()

        model.entryPoints.forEach { entryPoint ->
            materializationQueue.add(entryPoint.dependency)
        }

        val seenBindings = hashSetOf<Binding>()
        while (materializationQueue.isNotEmpty()) {
            val dependency = materializationQueue.removeFirst()
            val localBinding = materialize(dependency)
            if (localBinding == null) {
                val parentBinding = materializeInParents(dependency)
                if (parentBinding == null) {
                    missing += dependency.node
                    continue
                }
                // No need to add inherited binding's dependencies
            } else {
                if (!seenBindings.add(localBinding)) {
                    continue
                }
                if (localBinding is EmptyBinding && !dependency.kind.isOptional) {
                    missing += localBinding.target
                }
                materializationQueue += localBinding.dependencies()
            }
        }
        missingBindings = missing

        // Add all condition literals from all local bindings.
        for (binding in localBindings.keys) for (clause in binding.conditionScope.expression) for (literal in clause) {
            localConditionLiterals += literal
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

    private fun materialize(dependency: NodeModel.Dependency): Binding? {
        fun resolveAlias(maybeAlias: BaseBinding?): Binding? {
            var binding: BaseBinding? = maybeAlias
            while (true) {
                binding = when (binding) {
                    is AliasBinding -> materialize(NodeModel.Dependency(binding.source))
                    is Binding -> return binding
                    null -> return null
                }
            }
        }

        val (node, kind) = dependency
        val binding = allProvidedBindings.getOrPut(node) {
            node.implicitBinding(forGraph = this)?.takeIf { binding ->
                binding.scope == null || binding.scope == model.scope
            }
        }
        val nonAlias = resolveAlias(binding) ?: return null

        resolveHelper[node] = nonAlias
        localBindings.getOrPut(nonAlias, ::BindingUsageImpl).accept(kind)
        return nonAlias
    }

    private fun materializeInParents(dependency: NodeModel.Dependency): Binding? {
        if (parent == null) {
            return null
        }
        val binding = parent.materialize(dependency)
        if (binding != null) {
            // The binding is requested from a parent, so add parent to dependencies.
            usedParents += parent
            parent.materializationQueue += NodeModel.Dependency(binding.target)
            return binding
        }
        return parent.materializeInParents(dependency)?.also {
            usedParents += it.owner
        }
    }

    override fun toString(): String {
        return "Graph[$model]"
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

    fun accept(dependencyKind: NodeModel.Dependency.Kind) {
        when (dependencyKind) {
            NodeModel.Dependency.Kind.Direct -> _direct++
            NodeModel.Dependency.Kind.Lazy -> _lazy++
            NodeModel.Dependency.Kind.Provider -> _provider++
            NodeModel.Dependency.Kind.Optional -> _optional++
            NodeModel.Dependency.Kind.OptionalLazy -> _optionalLazy++
            NodeModel.Dependency.Kind.OptionalProvider -> _optionalProvider++
        }.let { /*exhaustive*/ }
    }
}

/**
 * Creates [BindingGraph] instance given the root component.
 */
fun BindingGraph(root: ComponentModel, modelFactory: LangModelFactory): BindingGraph {
    require(root.isRoot) { "can't use non-root component as a root of a binding graph" }
    return BindingGraphImpl(root, modelFactory)
}
