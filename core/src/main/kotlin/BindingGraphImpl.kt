package com.yandex.dagger3.core

internal class BindingGraphImpl(
    override val component: ComponentModel,
    private val parent: BindingGraphImpl? = null,
) : BindingGraph {
    private val allProvidedBindings: MutableMap<NodeModel, Binding?> = sequenceOf(
        component.modules.asSequence().flatMap(ModuleModel::bindings),
        component.factory?.inputs?.asSequence()?.filterIsInstance<InstanceBinding>() ?: emptySequence()
    ).flatten().associateByTo(mutableMapOf(), Binding::target)
    private val localBindingsMap = mutableMapOf<NodeModel, Binding>()

    override val localBindings: Collection<Binding> get() = localBindingsMap.values
    override val missingBindings: Set<NodeModel>  // Initialized in init block
    override val children: Collection<BindingGraphImpl> = component.modules
        .asSequence()
        .flatMap(ModuleModel::subcomponents)
        .distinct()
        .map { BindingGraphImpl(it, parent = this) }
        .toList()

    override fun resolveBinding(node: NodeModel): Pair<Binding, BindingGraph> {
        val resolved = localBindingsMap[node]?.let { it to this } ?: parent?.resolveBinding(node)
        return checkNotNull(resolved) { "No binding for $node" }
    }

    private fun actualize(node: NodeModel): Binding? {
        return allProvidedBindings.getOrPut(node) {
            // Take default binding only if the binding scope complies with the component one
            // TODO: don't report missing binding, report something smarter is user erred in class/component scope
            node.defaultBinding?.takeIf { it.scope == null || it.scope == component.scope }
        }?.also { binding ->
            localBindingsMap[binding.target] = binding
        }
    }

    private fun actualizeInParents(nodeModel: NodeModel): Pair<Binding, BindingGraphImpl>? {
        if (parent == null) {
            return null
        }
        val binding = parent.actualize(nodeModel)
        if (binding != null) {
            return binding to parent
        }
        return parent.actualizeInParents(nodeModel)
    }

    init {
        val queue = ArrayDeque<NodeModel>()
        component.entryPoints.forEach { entryPoint ->
            queue.add(entryPoint.dep.node)
        }
        val missing = mutableSetOf<NodeModel>()
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val localBinding = actualize(node)
            if (localBinding == null) {
                val parentBinding = actualizeInParents(node)
                if (parentBinding == null) {
                    missing += node
                    continue
                }
                // No need to add inherited binding dependencies
            } else {
                localBinding.dependencies().forEach { dependency ->
                    queue += dependency.node
                }
            }
        }
        missingBindings = missing
    }
}