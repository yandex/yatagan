package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.base.notIntersects
import com.yandex.daggerlite.core.AssistedInjectFactoryModel
import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.CollectionTargetKind
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentFactoryModel.InputPayload
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.HasNodeModel
import com.yandex.daggerlite.core.InjectConstructorModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel.BindingTargetModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.MultiBindingDeclarationModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.accept
import com.yandex.daggerlite.core.allInputs
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.TypeLangModel
import com.yandex.daggerlite.graph.AliasBinding
import com.yandex.daggerlite.graph.BaseBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.Extensible
import com.yandex.daggerlite.graph.ExtensibleBinding
import com.yandex.daggerlite.graph.MultiBinding.ContributionType
import com.yandex.daggerlite.graph.WithParents
import com.yandex.daggerlite.graph.parentsSequence
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.RichString
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.format.Strings
import com.yandex.daggerlite.validation.format.modelRepresentation
import com.yandex.daggerlite.validation.format.reportError

internal class GraphBindingsManager(
    private val graph: BindingGraph,
) : MayBeInvalid, WithParents<GraphBindingsManager> by hierarchyExtension(graph, GraphBindingsManager) {
    init {
        graph[GraphBindingsManager] = this
    }
    private val implicitBindingCreator = ImplicitBindingCreator()

    override fun toString(childContext: MayBeInvalid?): RichString = throw AssertionError("Not reached")

    private val providedBindings: Map<NodeModel, List<BaseBinding>> = buildList {
        val bindingModelVisitor = ModuleHostedBindingsCreator()

        // Gather bindings from modules
        val seenSubcomponents = hashSetOf<ComponentModel>()
        val listBindings = linkedMapOf<NodeModel, MutableMap<MultiBindingImpl.Contribution, ContributionType>>()
        val setBindings = linkedMapOf<NodeModel, MutableMap<MultiBindingImpl.Contribution, ContributionType>>()
        val mapBindings = linkedMapOf<MapSignature, MutableList<MapBindingImpl.Contribution>>()
        for (module: ModuleModel in graph.modules) {
            // All bindings from installed modules
            for (bindingModel in module.bindings) {
                val binding = bindingModel.accept(bindingModelVisitor)
                add(binding)
                // Handle multi-bindings
                when (val target = bindingModel.target) {
                    is BindingTargetModel.DirectMultiContribution -> {
                        val contribution = MultiBindingImpl.Contribution(
                            contributionDependency = binding.target,
                            origin = bindingModel,
                        )
                        when(target.kind) {
                            CollectionTargetKind.List -> listBindings
                            CollectionTargetKind.Set -> setBindings
                        }.getOrPut(target.node, ::mutableMapOf)[contribution] = ContributionType.Element
                    }
                    is BindingTargetModel.FlattenMultiContribution -> {
                        val contribution = MultiBindingImpl.Contribution(
                            contributionDependency = binding.target,
                            origin = bindingModel,
                        )
                        when(target.kind) {
                            CollectionTargetKind.List -> listBindings
                            CollectionTargetKind.Set -> setBindings
                        }.getOrPut(target.flattened, ::mutableMapOf)[contribution] = ContributionType.Collection
                    }
                    is BindingTargetModel.MappingContribution -> {
                        target.keyType?.let { keyType ->
                            target.keyValue?.let { keyValue ->
                                val signature = MapSignature(
                                    keyType = keyType,
                                    valueType = target.node,
                                )
                                mapBindings.getOrPut(signature, ::arrayListOf) += MapBindingImpl.Contribution(
                                    keyValue = keyValue,
                                    dependency = binding.target,  // will be m-b contribution node
                                    origin = bindingModel,
                                )
                            }
                        }
                    }
                    is BindingTargetModel.Plain -> Unit // Nothing to do
                }
            }
            // Subcomponent factories (distinct).
            for (subcomponent: ComponentModel in module.subcomponents) {
                if (seenSubcomponents.add(subcomponent)) {
                    subcomponent.factory?.let { factory: ComponentFactoryModel ->
                        add(SubComponentFactoryBindingImpl(
                            owner = graph,
                            factory = factory,
                        ))
                    }
                }
            }
        }
        // Gather dependencies
        for (dependency: ComponentDependencyModel in graph.dependencies) {
            // Binding for the dependency component itself.
            add(ComponentDependencyBindingImpl(dependency = dependency, owner = graph))
            // Bindings backed by the component entry-points.
            for ((node: NodeModel, getter: FunctionLangModel) in dependency.exposedDependencies)
                add(ComponentDependencyEntryPointBindingImpl(
                    owner = graph,
                    dependency = dependency,
                    target = node,
                    getter = getter,
                ))
        }
        val creator = graph.creator
        if (creator != null) {
            for (input: ComponentFactoryModel.InputModel in creator.allInputs) when (val payload = input.payload) {
                is InputPayload.Instance -> add(InstanceBindingImpl(
                    target = payload.node,
                    owner = graph,
                    origin = input,
                ))
                else -> {}
            }
        }

        for (module in graph.modules) for (declaration in module.multiBindingDeclarations) {
            declaration.accept(object : MultiBindingDeclarationModel.Visitor<Unit> {
                override fun visitInvalid(model: MultiBindingDeclarationModel.InvalidDeclarationModel) = Unit

                override fun visitCollectionDeclaration(model: MultiBindingDeclarationModel.CollectionDeclarationModel) {
                    val bindings = when (model.kind) {
                        CollectionTargetKind.List -> listBindings
                        CollectionTargetKind.Set -> setBindings
                    }
                    model.elementType?.let { elementType ->
                        bindings.getOrPut(elementType, ::mutableMapOf)
                    }
                }

                override fun visitMapDeclaration(model: MultiBindingDeclarationModel.MapDeclarationModel) {
                    model.keyType?.let { keyType ->
                        model.valueType?.let { valueType ->
                            mapBindings.getOrPut(MapSignature(keyType, valueType), ::mutableListOf)
                        }
                    }
                }
            })
        }

        // Multi-bindings
        addMultiBindings(
            listBindings,
            multiBindingKind = CollectionTargetKind.List,
        )
        addMultiBindings(
            setBindings,
            multiBindingKind = CollectionTargetKind.Set,
        )

        // Mappings
        for ((mapSignature, contributions) in mapBindings) {
            for (useProviders in booleanArrayOf(true, false)) {
                val (keyType: TypeLangModel, valueType: NodeModel) = mapSignature
                val nodes = valueType.multiBoundMapNodes(key = keyType, asProviders = useProviders)
                val representativeNode = nodes.first()
                val upstream = parentsSequence().mapNotNull { parentBindings ->
                    parentBindings.providedBindings[representativeNode]?.singleOrNull() as? MapBindingImpl
                }.firstOrNull()
                val downstreamNode = MultibindingDownstreamHandle(underlying = representativeNode)
                addBindingForAllNodes(
                    nodes = nodes + downstreamNode,
                ) {
                    MapBindingImpl(
                        owner = graph,
                        target = it,
                        contents = if (useProviders) {
                            contributions.map { contribution ->
                                contribution.copy(
                                    dependency = contribution.dependency.copyDependency(
                                        kind = DependencyKind.Provider,
                                    )
                                )
                            }
                        } else contributions,
                        mapKey = keyType,
                        mapValue = valueType.type,
                        upstream = upstream,
                        targetForDownstream = downstreamNode,
                    )
                }
            }
        }

        // This component binding
        add(ComponentInstanceBindingImpl(graph = graph))
    }.groupBy(BaseBinding::target)

    private val implicitBindings = mutableMapOf<NodeModel, Binding?>()

    fun getBindingFor(node: NodeModel): BaseBinding? {
        return implicitBindings[node] ?: providedBindings[node]?.first()
    }

    fun materializeMissing(node: NodeModel): Binding {
        return MissingBindingImpl(target = node, owner = graph).also {
            implicitBindings[node] = it
        }
    }

    fun materializeAliasLoop(node: NodeModel, chain: Collection<AliasBinding>): Binding {
        return AliasLoopStubBinding(owner = graph, target = node, aliasLoop = chain).also {
            implicitBindings[node] = it
        }
    }

    fun getExplicitBindingFor(node: NodeModel): BaseBinding? {
        return providedBindings[node]?.first()
    }

    fun materializeImplicitBindingFor(node: NodeModel): Binding? {
        return implicitBindings.getOrPut(node) {
            if (node.qualifier == null) {
                node.getSpecificModel().accept(implicitBindingCreator)
            } else null
        }
    }

    private inline fun MutableList<BaseBinding>.addBindingForAllNodes(
        nodes: Array<NodeModel>,
        block: (NodeModel) -> Binding,
    ) {
        val iterator = nodes.iterator()
        require(iterator.hasNext())
        var current = iterator.next()
        add(block(current))
        while (iterator.hasNext()) {
            val old = current
            current = iterator.next()
            add(SyntheticAliasBindingImpl(
                owner = graph,
                target = current,
                source = old,
            ))
        }
    }

    private fun MutableList<BaseBinding>.addMultiBindings(
        multiBindings: Map<NodeModel, MutableMap<MultiBindingImpl.Contribution, ContributionType>>,
        multiBindingKind: CollectionTargetKind,
    ) {
        for ((target: NodeModel, contributions) in multiBindings) {
            val nodes = when (multiBindingKind) {
                CollectionTargetKind.List -> target.multiBoundListNodes()
                CollectionTargetKind.Set -> target.multiBoundSetNodes()
            }
            val representativeNode = nodes.first()
            val upstream = parentsSequence().mapNotNull { parentBindings ->
                parentBindings.providedBindings[representativeNode]?.singleOrNull()
            }.firstOrNull() as? MultiBindingImpl
            val downstreamNode = MultibindingDownstreamHandle(underlying = representativeNode)
            addBindingForAllNodes(
                nodes = nodes + downstreamNode,
            ) {
                MultiBindingImpl(
                    owner = graph,
                    target = it,
                    upstream = upstream,
                    targetForDownstream = downstreamNode,
                    contributions = contributions,
                    kind = multiBindingKind,
                )
            }
        }
    }

    private val localAndParentExplicitBindings: Map<NodeModel, List<BaseBinding>> by lazy {
        mergeMultiMapsForDuplicateCheck(
            fromParent = graph.parent?.get(GraphBindingsManager)?.localAndParentExplicitBindings,
            current = providedBindings,
        )
    }

    override fun validate(validator: Validator) {
        val locallyRequestedNodes = graph.localBindings.map { (binding, _) -> binding.target }.toHashSet()
        for ((node, bindings) in localAndParentExplicitBindings) {
            if (node !in locallyRequestedNodes) {
                // Check duplicates only for locally requested bindings - no need to report parent duplicates.
                // As a side effect, if duplicates are present for an unused binding - we don't care.
                continue
            }

            if (bindings.size > 1) {
                val distinct = bindings.toSet()
                if (distinct.size > 1) {

                    // We tolerate multibinding duplicates, because of the "extends" behavior.
                    // There can be no two+ different multi-bindings for the same node in the same graph,
                    //  so here we definitely have bindings from different graphs - no need to check that.
                    if (distinct.all { it is ExtensibleBinding<*> }) continue

                    validator.reportError(Strings.Errors.conflictingBindings(`for` = node)) {
                        distinct.forEach { binding ->
                            addNote(Strings.Notes.duplicateBinding(binding))
                        }
                    }
                }
            }
        }
    }

    private inner class ModuleHostedBindingsCreator : ModuleHostedBindingModel.Visitor<BaseBinding> {
        override fun visitBinds(model: BindsBindingModel): BaseBinding {
            return if (model.target.node in model.sources) {
                SelfDependentInvalidBinding(
                    owner = graph,
                    impl = model,
                )
            } else when (model.sources.count()) {
                0 -> ExplicitEmptyBindingImpl(
                    owner = graph,
                    impl = model,
                )
                1 -> AliasBindingImpl(
                    owner = graph,
                    impl = model,
                )
                else -> AlternativesBindingImpl(
                    owner = graph,
                    impl = model,
                )
            }
        }

        override fun visitProvides(model: ProvidesBindingModel): BaseBinding {
            return if (model.target.node in model.inputs.map(NodeDependency::node)) SelfDependentInvalidBinding(
                owner = graph,
                impl = model,
            ) else ProvisionBindingImpl(
                impl = model,
                owner = graph,
            )
        }
    }

    private inner class ImplicitBindingCreator : HasNodeModel.Visitor<Binding?> {
        override fun visitDefault(): Binding? = null

        override fun visitInjectConstructor(model: InjectConstructorModel): Binding? {
            if (model.scopes.isNotEmpty() && model.scopes notIntersects graph.scopes) {
                return null
            }

            return InjectConstructorProvisionBindingImpl(
                impl = model,
                owner = graph,
            )
        }

        override fun visitAssistedInjectFactory(model: AssistedInjectFactoryModel): Binding {
            return AssistedInjectFactoryBindingImpl(
                model = model,
                owner = graph,
            )
        }
    }

    private class MultibindingDownstreamHandle(
        val underlying: NodeModel,
    ) : NodeModel by underlying {
        override fun getSpecificModel(): Nothing? = null
        override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
            modelClassName = "inherited-multi-binding",
            representation = underlying.toString(childContext = null),
        )

        override val node: NodeModel get() = this
    }

    private data class MapSignature(
        val keyType: TypeLangModel,
        val valueType: NodeModel,
    )

    companion object Key : Extensible.Key<GraphBindingsManager> {
        override val keyType get() = GraphBindingsManager::class.java
    }
}
