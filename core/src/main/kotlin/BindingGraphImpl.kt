package com.yandex.dagger3.core

import com.yandex.dagger3.core.NodeModel.Dependency.Kind

internal class BindingGraphImpl(
    override val component: ComponentModel,
    private val parent: BindingGraphImpl? = null,
) : BindingGraph {
    private val allProvidedBindings: MutableMap<NodeModel, Binding?> = sequenceOf(
        component.modules.asSequence().flatMap(ModuleModel::bindings),
        component.factory?.inputs?.asSequence()?.filterIsInstance<InstanceBinding>() ?: emptySequence()
    ).flatten().onEach { it.owner = this }.associateByTo(mutableMapOf(), Binding::target)
    private val localBindingsMap = mutableMapOf<NodeModel, Binding>()

    override val localBindings = mutableMapOf<Binding, BindingUsageImpl>()
    override val missingBindings: Set<NodeModel>  // Initialized in init block
    override val children: Collection<BindingGraphImpl> = component.modules
        .asSequence()
        .flatMap(ModuleModel::subcomponents)
        .distinct()
        .map { BindingGraphImpl(it, parent = this) }
        .toList()
    override val usedParents = mutableSetOf<BindingGraph>()

    override fun resolveBinding(node: NodeModel): Binding {
        return localBindingsMap[node] ?: parent?.resolveBinding(node) ?: throw MissingBindingException(node)
    }

    private fun actualize(dependency: NodeModel.Dependency): Binding? {
        val (node, kind) = dependency
        return allProvidedBindings.getOrPut(node) {
            // Take default binding only if the binding scope complies with the component one
            // TODO: don't report missing binding, report something smarter is user erred in class/component scope
            node.defaultBinding?.takeIf {
                val scope = it.scope()
                scope == null || scope == component.scope
            }?.also {
                it.owner = this
            }
        }?.also { binding ->
            localBindingsMap[binding.target] = binding
            localBindings.getOrPut(binding, ::BindingUsageImpl)
                .accept(kind)
        }
    }

    private fun actualizeInParents(dependency: NodeModel.Dependency): Binding? {
        if (parent == null) {
            return null
        }
        val binding = parent.actualize(dependency)
        if (binding != null) {
            // The binding is requested from a parent, so add parent to dependencies.
            usedParents += parent
            return binding
        }
        return parent.actualizeInParents(dependency)?.also {
            usedParents += it.owner
        }
    }

    init {
        val queue = ArrayDeque<NodeModel.Dependency>()
        component.entryPoints.forEach { entryPoint ->
            queue.add(entryPoint.dep)
        }
        val missing = mutableSetOf<NodeModel>()
        while (queue.isNotEmpty()) {
            val dependency = queue.removeFirst()
            val localBinding = actualize(dependency)
            if (localBinding == null) {
                val parentBinding = actualizeInParents(dependency)
                if (parentBinding == null) {
                    missing += dependency.node
                    continue
                }
                // No need to add inherited binding's dependencies
            } else {
                queue += localBinding.dependencies()
            }
        }
        missingBindings = missing
    }

    class BindingUsageImpl : BindingUsage {
        override var direct: Int = 0
        override var provider: Int = 0
        override var lazy: Int = 0
    }

    private fun BindingUsageImpl.accept(dependencyKind: Kind) {
        when (dependencyKind) {
            Kind.Direct -> direct++
            Kind.Lazy -> lazy++
            Kind.Provider -> provider++
        }.let { /*exhaustive*/ }
    }
}