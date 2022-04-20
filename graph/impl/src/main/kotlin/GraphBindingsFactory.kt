package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.base.ifContainsDuplicates
import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentFactoryModel.InputPayload
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.HasNodeModel
import com.yandex.daggerlite.core.InjectConstructorModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel.BindingTargetModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.accept
import com.yandex.daggerlite.core.allInputs
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.graph.AliasBinding
import com.yandex.daggerlite.graph.BaseBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.MultiBinding.ContributionType
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.ValidationMessage
import com.yandex.daggerlite.validation.ValidationMessage.Kind
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.ValidationMessageBuilder
import com.yandex.daggerlite.validation.impl.buildMessage

internal class GraphBindingsFactory(
    private val graph: BindingGraphImpl,
    private val parent: GraphBindingsFactory?,
) : MayBeInvalid {
    private val implicitBindingDeduct = ImplicitBindingDeduct()
    private val validationMessages = arrayListOf<ValidationMessage>()

    private val providedBindings: Map<NodeModel, List<BaseBinding>> = buildList {
        val bindingModelVisitor = object : ModuleHostedBindingModel.Visitor<BaseBinding> {
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

        // Gather bindings from modules
        val seenSubcomponents = hashSetOf<ComponentModel>()
        val multiBindings = linkedMapOf<NodeModel, MutableMap<NodeModel, ContributionType>>()
        // TODO: In vanilla dagger, multibindings are inherited and accumulated from parents.
        for (module: ModuleModel in graph.modules) {
            // All bindings from installed modules
            for (bindingModel in module.bindings) {
                val binding = bindingModel.accept(bindingModelVisitor)
                add(binding)
                // Handle multi-bindings
                when (val target = bindingModel.target) {
                    is BindingTargetModel.DirectMultiContribution -> {
                        multiBindings.getOrPut(target.node, ::mutableMapOf)[binding.target] =
                            ContributionType.Element
                    }
                    is BindingTargetModel.FlattenMultiContribution -> {
                        multiBindings.getOrPut(target.flattened, ::mutableMapOf)[binding.target] =
                            ContributionType.Collection
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

        graph.modules.asSequence()
            .flatMap { it.listDeclarations }
            .forEach {
                // Provide empty map for an empty list
                multiBindings.getOrPut(it.listType, ::mutableMapOf)
            }

        // Multi-bindings
        for ((target: NodeModel, contributions: Map<NodeModel, ContributionType>) in multiBindings) {
            val iterator = target.multiBoundListNodes().iterator()
            if (!iterator.hasNext()) continue
            var current = iterator.next()
            add(MultiBindingImpl(
                owner = graph,
                target = current,
                contributions = contributions,
            ))
            while(iterator.hasNext()) {
                val old = current
                current = iterator.next()
                add(SyntheticAliasBindingImpl(
                    owner = graph,
                    target = current,
                    source = old,
                ))
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
        return implicitBindings.getOrPut(node, fun(): Binding? {
            if (node.qualifier != null) {
                return null
            }
            return node.getSpecificModel().accept(implicitBindingDeduct)
        })
    }

    private fun localAndParentExplicitBindings(): Map<NodeModel, List<BaseBinding>> {
        return buildMap<NodeModel, MutableList<BaseBinding>> {
            parent?.localAndParentExplicitBindings()?.forEach { (node, bindings) ->
                getOrPut(node, ::arrayListOf) += bindings
            }
            providedBindings.forEach { (node, bindings) ->
                getOrPut(node, ::arrayListOf) += bindings
            }
        }
    }

    override fun validate(validator: Validator) {
        validationMessages.forEach(validator::report)

        val locallyRequestedNodes = graph.localBindings.map { (binding, _) -> binding.target }.toHashSet()
        for ((node, bindings) in localAndParentExplicitBindings()) {
            if (node !in locallyRequestedNodes) {
                // Check duplicates only for locally requested bindings - no need to report parent duplicates.
                // As a side effect, if duplicates are present for an unused binding - we don't care.
                continue
            }

            bindings.ifContainsDuplicates { duplicates ->
                validator.report(buildMessage(Kind.Error, fun ValidationMessageBuilder.() {
                    contents = Strings.Errors.conflictingBindings(`for` = node)
                    duplicates.forEach { binding ->
                        addNote(Strings.Notes.duplicateBinding(binding))
                    }
                }))
            }
        }
    }

    private inner class ImplicitBindingDeduct : HasNodeModel.Visitor<Binding?> {
        override fun visitDefault(): Binding? = null

        override fun visitInjectConstructor(model: InjectConstructorModel): Binding? {
            if (model.scope != null && model.scope != graph.scope) {
                return null
            }

            return InjectConstructorProvisionBindingImpl(
                impl = model,
                owner = graph,
            )
        }
    }
}
