package com.yandex.daggerlite.testing.generation

data class GenerationParams(
    /**
     * Maximum components (and logical scopes) hierarchy depth.
     */
    val componentTreeMaxDepth: Int,

    /**
     * Total number of unique nodes in the graph, across all hierarchies.
     */
    val totalGraphNodesCount: Int,

    /**
     * Max number of bindings to be available for a single given node,
     * so different bindings may be used in different graphs.
     */
    val maxBindingsPerNode: Int = 2,

    /**
     * Distribution of binding types.
     */
    val bindings: Distribution<BindingType>,

    /**
     * Binary distribution of binding "scoped" (cacheable) status.
     */
    val bindingScopes: Distribution<ProvisionScopes> = Distribution.uniform(),

    /**
     * Maximum number of @provides/@inject binding's dependencies.
     */
    val maxProvisionDependencies: Int,

    /**
     * Dependency kinds distribution for provisions (inject/provides)
     */
    val provisionDependencyKind: Distribution<DependencyKind>,

    /**
     * Maximum number of entrypoints for a component.
     */
    val maxEntryPointsPerComponent: Int,

    /**
     * Maximum number of member injectors per component.
     */
    val maxMemberInjectorsPerComponent: Int,
    val maxMembersPerInjectee: Int,

    /**
     * Total number of separate logic scope hierarchy roots.
     */
    val totalRootScopes: Int = 1,

    /**
     * Maximum number of logical child scopes per logical scope.
     */
    val maxChildrenPerScope: Int = 3,

    /**
     * A divider factor that is applied to [maxChildrenPerScope] with each depth level.
     */
    val maxChildrenPerScopeDepthDecay: Double = 2.0,

    /**
     * Total component hierarchy roots count.
     */
    val totalRootCount: Int,

    /**
     * Maximum number of subcomponents that a component can have.
     */
    val maxChildrenPerComponent: Int,

    val seed: Long,
) {
    init {
        check(componentTreeMaxDepth > 0) { "Component tree can't have zero depth" }
        check(maxChildrenPerScopeDepthDecay >= 1.0) { "Decay divider can't be less than 1.0" }
    }

    enum class DependencyKind {
        Direct,
        Lazy,
        Provider,
    }

    enum class BindingType {
        Inject,
        Provision,
        Alias,
        Instance,
        ComponentDependency,
        ComponentDependencyEntryPoint,
    }

    enum class ProvisionScopes {
        Scoped,
        Unscoped,
    }
}