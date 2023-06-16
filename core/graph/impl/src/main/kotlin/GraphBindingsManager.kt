/*
 * Copyright 2022 Yandex LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yandex.yatagan.core.graph.impl

import com.yandex.yatagan.base.api.Extensible
import com.yandex.yatagan.base.api.WithParents
import com.yandex.yatagan.base.api.parentsSequence
import com.yandex.yatagan.core.graph.bindings.AliasBinding
import com.yandex.yatagan.core.graph.bindings.BaseBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.ExtensibleBinding
import com.yandex.yatagan.core.graph.bindings.MultiBinding.ContributionType
import com.yandex.yatagan.core.graph.bindings.SubComponentBinding
import com.yandex.yatagan.core.graph.impl.bindings.AliasBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.AliasLoopStubBinding
import com.yandex.yatagan.core.graph.impl.bindings.AlternativesBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.AssistedInjectFactoryBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.ComponentDependencyBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.ComponentDependencyEntryPointBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.ComponentInstanceBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.ExplicitEmptyBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.InjectConstructorProvisionBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.InstanceBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.IntrinsicBindingMarker
import com.yandex.yatagan.core.graph.impl.bindings.MapBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.MissingBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.MultiBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.ProvisionBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.SelfDependentInvalidBinding
import com.yandex.yatagan.core.graph.impl.bindings.SubComponentBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.SubComponentFactoryBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.SyntheticAliasBindingImpl
import com.yandex.yatagan.core.graph.impl.bindings.canHost
import com.yandex.yatagan.core.graph.impl.bindings.maybeUnwrapSyntheticAlias
import com.yandex.yatagan.core.model.AssistedInjectFactoryModel
import com.yandex.yatagan.core.model.BindsBindingModel
import com.yandex.yatagan.core.model.CollectionTargetKind
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ComponentFactoryModel.InputPayload
import com.yandex.yatagan.core.model.ComponentFactoryWithBuilderModel
import com.yandex.yatagan.core.model.ComponentModel
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.HasNodeModel
import com.yandex.yatagan.core.model.InjectConstructorModel
import com.yandex.yatagan.core.model.ModuleHostedBindingModel
import com.yandex.yatagan.core.model.ModuleHostedBindingModel.BindingTargetModel
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.MultiBindingDeclarationModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.ProvidesBindingModel
import com.yandex.yatagan.core.model.SubComponentFactoryMethodModel
import com.yandex.yatagan.core.model.accept
import com.yandex.yatagan.core.model.allInputs
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.Type
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.RichString
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError

internal class GraphBindingsManager(
    private val graph: BindingGraphImpl,
    subcomponents: Map<ComponentModel, ComponentFactoryModel?>,
) : MayBeInvalid, WithParents<GraphBindingsManager> by hierarchyExtension(graph, GraphBindingsManager) {
    init {
        graph[GraphBindingsManager] = this
    }
    private val implicitBindingCreator = ImplicitBindingCreator()

    override fun toString(childContext: MayBeInvalid?): RichString = throw AssertionError("Not reached")

    private val providedBindings: Map<NodeModel, List<BaseBinding>> = buildList {
        val bindingModelVisitor = ModuleHostedBindingsCreator()

        // Gather bindings from modules
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
        }

        // Subcomponent factories (distinct).
        for ((subcomponent, factory) in subcomponents.entries) {
            factory.accept(SubComponentBindingCreator(subcomponent))?.let(::add)
        }
        // Gather dependencies
        for (dependency: ComponentDependencyModel in graph.dependencies) {
            // Binding for the dependency component itself.
            add(ComponentDependencyBindingImpl(dependency = dependency, owner = graph))
            // Bindings backed by the component entry-points.
            for ((node: NodeModel, getter: Method) in dependency.exposedDependencies)
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
                    target = payload.model,
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
                val (keyType: Type, valueType: NodeModel) = mapSignature
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
        val theBinding = block(current).also { add(it) }
        while (iterator.hasNext()) {
            current = iterator.next()
            add(SyntheticAliasBindingImpl(
                target = current,
                sourceBinding = theBinding,
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
        for ((node, bindings) in localAndParentExplicitBindings) {
            if (node !in graph.localNodes) {
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
                    if (distinct.all { it.maybeUnwrapSyntheticAlias() is ExtensibleBinding<*> }) continue

                    // Intrinsic bindings are allowed to override each other in child graphs.
                    if (distinct.all { it.maybeUnwrapSyntheticAlias() is IntrinsicBindingMarker }) {
                        assert(distinct.distinctBy { it.owner }.size == distinct.size) {
                            "Not reached: duplicate intrinsic bindings in one graph"
                        }
                        continue
                    }

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
            if (!graph.canHost(model.scopes)) {
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

    private inner class SubComponentBindingCreator(
        private val subcomponent: ComponentModel,
    ) : ComponentFactoryVisitor<SubComponentBinding?> {
        override fun visitNull(): SubComponentBinding {
            // No factory at all, bind a factory to an instance
            return SubComponentBindingImpl(
                owner = graph,
                targetComponent = subcomponent,
            )
        }

        override fun visitSubComponentFactoryMethod(model: SubComponentFactoryMethodModel): Nothing? {
            // Factory method, no factory object or child component instance can be bound
            return null
        }

        override fun visitWithBuilder(model: ComponentFactoryWithBuilderModel): SubComponentBinding {
            // Explicit @Component.Builder, create a binding for it
            return SubComponentFactoryBindingImpl(
                owner = graph,
                factory = model,
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
        val keyType: Type,
        val valueType: NodeModel,
    )

    companion object Key : Extensible.Key<GraphBindingsManager> {
        override val keyType get() = GraphBindingsManager::class.java
    }
}
