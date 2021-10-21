package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.NodeModel.Dependency.Kind

internal class BindingGraphImpl(
    override val model: ComponentModel,
    private val parent: BindingGraphImpl? = null,
) : BindingGraph {
    private val resolveHelper = hashMapOf<NodeModel, Binding>()
    private val allProvidedBindings: MutableMap<NodeModel, BaseBinding?> = sequence {
        // Gather bindings from modules
        val seenSubcomponents = hashSetOf<ComponentModel>()
        for (module: ModuleModel in model.modules) {
            // All bindings from installed modules
            for (binding: BaseBinding in module.bindings)
                yield(binding)
            // Subcomponent factories (distinct).
            for (subcomponent: ComponentModel in module.subcomponents) {
                if (seenSubcomponents.add(subcomponent)) {
                    // MAYBE: Factory is actually required.
                    subcomponent.factory?.let { factory: ComponentFactoryModel ->
                        yield(SubComponentFactoryBinding(factory))
                    }
                }
            }
        }
        // Gather bindings from factory
        model.factory?.let { factory: ComponentFactoryModel ->
            for (input: ComponentFactoryModel.Input in factory.inputs) when (input) {
                is ComponentDependencyFactoryInput -> {
                    // Binding for the dependency component itself.
                    yield(input)
                    // Bindings backed by the component entry-points.
                    for (entryPoint: ComponentModel.EntryPoint in input.target.entryPoints)
                        if (entryPoint.dependency.kind == Kind.Direct)
                            yield(DependencyComponentEntryPointBinding(input, entryPoint))
                }
                // Instance binding
                is InstanceBinding -> yield(input)
                is ModuleInstanceFactoryInput -> TODO("support module instances")
            }.let { /*exhaustive*/ }
        }
        // This component binding
        yield(ComponentInstanceBinding())
    }.onEach { binding: BaseBinding -> binding.owner = this }
        .associateByTo(mutableMapOf(), BaseBinding::target)

    override val localBindings = mutableMapOf<Binding, BindingUsageImpl>()
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
            .distinct()
            .map { BindingGraphImpl(it, parent = this) }
            .toList()

        val missing = hashSetOf<NodeModel>()

        model.entryPoints.forEach { entryPoint ->
            materializationQueue.add(entryPoint.dependency)
        }

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
                when (localBinding) {
                    is ProvisionBinding -> {
                        materializationQueue += localBinding.params
                    }
                    is SubComponentFactoryBinding -> {
                        val createdComponent = localBinding.target.createdComponent
                        val targetGraph = checkNotNull(children.find { it.model == createdComponent })
                        localBinding.targetGraph = targetGraph
                        targetGraph.usedParents.forEach { graph ->
                            materializationQueue += NodeModel.Dependency(graph.model)
                        }
                    }
                    is DependencyComponentEntryPointBinding -> {
                        materializationQueue += NodeModel.Dependency(localBinding.input.target)
                    }
                    // no dependencies for instances
                    is ComponentDependencyFactoryInput,
                    is ComponentInstanceBinding,
                    is InstanceBinding -> Unit
                }.let { /*exhaustive*/ }
            }
        }
        missingBindings = missing

        // No longer required
//        allProvidedBindings.clear()

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
            node.implicitBinding()?.takeIf {
                val scope = it.scope()
                scope == null || scope == model.scope
            }?.also {
                it.owner = this
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

class BindingUsageImpl : BindingUsage {
    override var direct: Int = 0
        private set
    override var provider: Int = 0
        private set
    override var lazy: Int = 0
        private set

    fun accept(dependencyKind: Kind) {
        when (dependencyKind) {
            Kind.Direct -> direct++
            Kind.Lazy -> lazy++
            Kind.Provider -> provider++
        }.let { /*exhaustive*/ }
    }
}
