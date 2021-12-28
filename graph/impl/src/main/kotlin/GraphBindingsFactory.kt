package com.yandex.daggerlite.graph.impl

import com.yandex.daggerlite.base.duplicateAwareAssociateBy
import com.yandex.daggerlite.base.ifContainsDuplicates
import com.yandex.daggerlite.core.BindsBindingModel
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ComponentFactoryModel.InputPayload
import com.yandex.daggerlite.core.ComponentModel
import com.yandex.daggerlite.core.ListDeclarationModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel
import com.yandex.daggerlite.core.ModuleHostedBindingModel.MultiBindingKind
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.ProvidesBindingModel
import com.yandex.daggerlite.core.allInputs
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.graph.BaseBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.MultiBinding.ContributionType
import com.yandex.daggerlite.validation.MayBeInvalid
import com.yandex.daggerlite.validation.ValidationMessage
import com.yandex.daggerlite.validation.Validator
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.buildError

internal class GraphBindingsFactory(
    modules: Set<ModuleModel>,
    dependencies: Set<ComponentDependencyModel>,
    factory: ComponentFactoryModel?,
    private val graph: BindingGraphImpl,
    private val scope: AnnotationLangModel?,
) : MayBeInvalid {
    private val validationMessages = arrayListOf<ValidationMessage>()

    private val providedBindings: Map<NodeModel, List<BaseBinding>> = buildList {
        val bindingModelVisitor = object : ModuleHostedBindingModel.Visitor<BaseBinding> {
            override fun visitBinds(model: BindsBindingModel): BaseBinding {
                return when (model.sources.count()) {
                    0 -> ModuleHostedEmptyBindingImpl(
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
                return ProvisionBindingImpl(
                    impl = model,
                    owner = graph,
                )
            }
        }

        // Gather bindings from modules
        val seenSubcomponents = hashSetOf<ComponentModel>()
        val multiBindings = linkedMapOf<NodeModel, MutableMap<NodeModel, ContributionType>>()
        for (module: ModuleModel in modules) {
            // All bindings from installed modules
            for (bindingModel in module.bindings) {
                val binding = bindingModel.accept(bindingModelVisitor)
                add(binding)
                // Handle multi-bindings
                bindingModel.multiBinding?.let { kind ->
                    multiBindings.getOrPut(bindingModel.target, ::mutableMapOf)[binding.target] = when (kind) {
                        MultiBindingKind.Direct -> ContributionType.Element
                        MultiBindingKind.Flatten -> ContributionType.Collection
                    }
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
        // Gather bindings from factory
        for (dependency: ComponentDependencyModel in dependencies) {
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
        if (factory != null) {
            for (input: ComponentFactoryModel.InputModel in factory.allInputs) when (val payload = input.payload) {
                is InputPayload.Instance -> add(InstanceBindingImpl(
                    target = payload.node,
                    owner = graph,
                ))
                else -> {}
            }
        }

        val declaredLists: Map<NodeModel, ListDeclarationModel> = modules.asSequence()
            .flatMap { it.listDeclarations }
            .onEach {
                // Provide empty map for an empty list
                multiBindings.getOrPut(it.listType, ::mutableMapOf)
            }
            .duplicateAwareAssociateBy(onDuplicates = { listNode, duplicateDeclarations ->
                validationMessages += buildError {
                    contents = Strings.Errors.`conflicting list declarations`(`for` = listNode)
                    duplicateDeclarations.forEachIndexed { i, duplicate ->
                        addNote("${i + 1}. $duplicate")
                    }
                }
            }, keySelector = ListDeclarationModel::listType)

        // Multi-bindings
        for ((target: NodeModel, contributions: Map<NodeModel, ContributionType>) in multiBindings) {
            add(MultiBindingImpl(
                owner = graph,
                target = target.multiBoundListNode(),
                contributions = contributions,
                declaration = declaredLists[target]
            ))
        }

        // This component binding
        add(ComponentInstanceBindingImpl(graph = graph))
    }.groupBy(BaseBinding::target)

    private val implicitBindings = mutableMapOf<NodeModel, Binding?>()

    fun getBindingFor(node: NodeModel): BaseBinding? {
        return implicitBindings[node] ?: providedBindings[node]?.first()
    }

    fun materializeMissing(node: NodeModel): Binding {
        return MissingBindingImpl(node, graph).also {
            implicitBindings[node] = it
        }
    }

    fun materializeBindingFor(node: NodeModel): BaseBinding? {
        // fixme: implicit binding should only be queried after parents are checked, not like this.
        //  otherwise it could conflict with explicit bindings in parents if any. Though this is
        //  actually not a good situation. And actually not checked now.
        return providedBindings[node]?.first() ?: implicitBindings.getOrPut(node, fun(): Binding? {
            if (node.qualifier != null) {
                return null
            }
            val inject = node.implicitBinding ?: return null

            if (inject.scope != null && inject.scope != scope) {
                return null
            }

            return InjectConstructorProvisionBindingImpl(
                impl = inject,
                owner = graph,
            )
        })
    }

    override fun validate(validator: Validator) {
        validationMessages.forEach(validator::report)

        // fixme: check duplicates with parent graphs also.
        //  as written now, "overriding" or "shadowing" of parent bindings is allowed.
        providedBindings.forEach { (node, bindings) ->
            bindings.ifContainsDuplicates { duplicates ->
                validator.report(buildError {
                    contents = Strings.Errors.`conflicting bindings`(`for` = node)
                    duplicates.forEach { binding ->
                        addNote("Duplicate binding: $binding")
                    }
                })
            }
        }
    }
}
