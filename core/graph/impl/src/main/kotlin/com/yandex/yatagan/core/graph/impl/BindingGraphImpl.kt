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

import com.yandex.yatagan.base.ExtensibleImpl
import com.yandex.yatagan.base.api.childrenSequence
import com.yandex.yatagan.base.api.parentsSequence
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.BindingGraph.LiteralUsage
import com.yandex.yatagan.core.graph.GraphSubComponentFactoryMethod
import com.yandex.yatagan.core.graph.bindings.AliasBinding
import com.yandex.yatagan.core.graph.bindings.AssistedInjectFactoryBinding
import com.yandex.yatagan.core.graph.bindings.BaseBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.model.AssistedInjectFactoryModel
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ComponentFactoryWithBuilderModel
import com.yandex.yatagan.core.model.ComponentModel
import com.yandex.yatagan.core.model.ConditionModel
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.HasNodeModel
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.ScopeModel
import com.yandex.yatagan.core.model.Variant
import com.yandex.yatagan.core.model.accept
import com.yandex.yatagan.instrumentation.impl.Instrumentation
import com.yandex.yatagan.instrumentation.impl.instrumentedDependencies
import com.yandex.yatagan.validation.MayBeInvalid
import com.yandex.yatagan.validation.Validator
import com.yandex.yatagan.validation.format.Strings
import com.yandex.yatagan.validation.format.modelRepresentation
import com.yandex.yatagan.validation.format.reportError

internal class BindingGraphImpl(
    private val component: ComponentModel,
    internal val options: Options,
    override val parent: BindingGraphImpl? = null,
    override val conditionScope: ConditionScope = ConditionScope.Always,
    private val factoryMethodInParent: ComponentFactoryModel? = null,
) : BindingGraph, ExtensibleImpl() {
    override val model: ComponentModel
        get() = component

    override val isRoot: Boolean
        get() = component.isRoot

    override val variant: Variant = component.variant + parent?.variant

    override val scopes: Set<ScopeModel>
        get() = component.scopes

    override val creator: ComponentFactoryModel?
        get() = factoryMethodInParent ?: component.factory

    override val modules: Collection<ModuleModel>
        get() = component.allModules

    override val dependencies: Collection<ComponentDependencyModel>
        get() = component.dependencies

    override val entryPoints = component.entryPoints.map { GraphEntryPointImpl(graph = this, impl = it) }

    override val memberInjectors = component.memberInjectors.map { GraphMemberInjectorImpl(owner = this, impl = it) }

    override val subComponentFactoryMethods: Collection<GraphSubComponentFactoryMethod> =
        component.subComponentFactoryMethods.map { GraphSubComponentFactoryMethodImpl(owner = this, model = it) }

    override val requiresSynchronizedAccess: Boolean
        get() = component.requiresSynchronizedAccess

    val childrenModels: Set<ComponentModel> = buildSet {
        // hierarchy loop guard
        if (parentsSequence().none { it.model == model }) {
            for (module in modules) {
                addAll(module.subcomponents)
            }
        }
        val hierarchy = parentsSequence(includeThis = true)
            .mapTo(hashSetOf()) { it.model }
        // Detect subcomponents (directly or via factories) and add them as children.
        val detector = object : HasNodeModel.Visitor<ComponentModel?> {
            override fun visitOther() = null
            override fun visitComponent(model: ComponentModel) = model
            override fun visitComponentFactory(model: ComponentFactoryWithBuilderModel) = model.createdComponent
        }
        for (entryPoint in entryPoints) {
            val component = entryPoint.dependency.node.getSpecificModel().accept(detector) ?: continue
            if (!component.isRoot && component !in hierarchy) {
                add(component)
            }
        }
        for (factory in component.subComponentFactoryMethods) {
            if (factory.createdComponent !in hierarchy) {
                add(factory.createdComponent)
            }
        }
    }

    private val bindings: GraphBindingsManager = GraphBindingsManager(
        graph = this,
        subcomponents = childrenModels.associateWith { child ->
            child.factory ?: component.subComponentFactoryMethods.find { it.createdComponent == child }
        },
    )

    internal val localNodes = mutableSetOf<NodeModel>()
    override val localBindings = mutableMapOf<Binding, BindingUsageImpl>()
    override val localConditionLiterals = mutableMapOf<ConditionModel, LiteralUsage>()
    override val localAssistedInjectFactories = mutableSetOf<AssistedInjectFactoryModel>()
    override val usedParents = mutableSetOf<BindingGraph>()
    override val children: Collection<BindingGraphImpl>

    private val instrumentedEntryPoints = mutableListOf<NodeModel>()

    private val aliasResolveVisitor = object : BaseBinding.Visitor<Binding> {
        override fun visitAlias(alias: AliasBinding) = resolveBindingRaw(alias.source).accept(this)
        override fun visitBinding(binding: Binding) = binding
        override fun visitOther(other: BaseBinding) = throw AssertionError()
    }

    override fun resolveBinding(node: NodeModel): Binding {
        return resolveBindingRaw(node).accept(aliasResolveVisitor)
    }

    override fun resolveBindingRaw(node: NodeModel): BaseBinding {
        return bindings.getBindingFor(node)
            ?: parent?.resolveBindingRaw(node)
            ?: throw IllegalStateException("Not reached: missing binding for ${node.toString(null)}")
    }

    // MAYBE: write algorithm in such a way that this is local variable.
    private val materializationQueue: MutableList<NodeDependency> = ArrayDeque()

    init {
        // Build children
        children = childrenModels
            .asSequence()
            .map { it to VariantMatch(it, variant).conditionScope }
            .filter { (_, conditionScope) -> !conditionScope.isContradiction() }
            .map { (childComponent, childConditionScope) ->
                BindingGraphImpl(
                    component = childComponent,
                    options = options,
                    parent = this,
                    conditionScope = conditionScope and childConditionScope,
                    factoryMethodInParent = component.subComponentFactoryMethods.find {
                        it.createdComponent == childComponent
                    },
                )
            }
            .toList()

        entryPoints.forEach { entryPoint ->
            materializationQueue.add(entryPoint.dependency)
        }
        memberInjectors.forEach { membersInjector ->
            membersInjector.membersToInject.values.forEach { injectDependency ->
                materializationQueue.add(injectDependency)
            }
        }

        doMaterialization()

        // |=> At this point we presume that this BindingGraph object is in a valid state.
        // Instrumentation plugins can now safely perform introspections on it.
        val applicablePlugins = options.instrumentationPlugins.filter { it.shouldInstrument(this) }
        if (applicablePlugins.isNotEmpty()) {
            for (plugin in applicablePlugins) {
                val instrumentation = Instrumentation(plugin)
                instrumentation.instrument(this)
                instrumentation.newDependencies.mapTo(instrumentedEntryPoints, NodeDependency::node)
                materializationQueue += instrumentation.newDependencies
            }

            val instrumentedBindings = hashSetOf<Binding>()
            while (materializationQueue.isNotEmpty()) {
                doMaterialization()

                if (instrumentedBindings.size == localBindings.size) {
                    // Early bail - no new bindings
                    break
                }

                for (plugin in applicablePlugins) {
                    val instrumentation = Instrumentation(plugin)
                    for (binding in localBindings.keys) {
                        if (instrumentedBindings.add(binding)) {
                            // Not-yet-instrumented binding
                            instrumentation.instrument(binding)
                        }
                    }
                    materializationQueue += instrumentation.newDependencies
                }
            }
        }
    }

    private fun doMaterialization() {
        val seenBindings = hashSetOf<Binding>()
        while (materializationQueue.isNotEmpty()) {
            val dependency = materializationQueue.removeFirst()
            val binding: BaseBinding = materialize(dependency)
            class AliasMaterializeVisitor : BaseBinding.Visitor<Binding> {
                var aliases = mutableSetOf<AliasBinding>()
                override fun visitAlias(alias: AliasBinding): Binding {
                    val carryDependency = dependency.copyDependency(node = alias.source)
                    if (!aliases.add(alias)) {
                        // Alias loop detected, bail out.
                        return bindings.materializeAliasLoop(node = dependency.node, chain = aliases)
                    }
                    return materialize(carryDependency).accept(this)
                }
                override fun visitBinding(binding: Binding) = binding
                override fun visitOther(other: BaseBinding) = throw AssertionError()
            }
            localNodes.add(dependency.node)
            // MAYBE: employ local alias resolution cache
            val nonAlias = binding.accept(AliasMaterializeVisitor())
            if (nonAlias.owner == this) {
                localBindings.getOrPut(nonAlias, ::BindingUsageImpl).accept(dependency.kind)

                if (!seenBindings.add(nonAlias)) {
                    continue
                }
                materializationQueue += nonAlias.dependencies
                materializationQueue += nonAlias.nonStaticConditionProviders
                materializationQueue += nonAlias.instrumentedDependencies()
            }
        }

        // Add all local assisted binding factories.
        for (binding in localBindings.keys) {
            binding.accept(object : Binding.Visitor<Unit> {
                override fun visitOther(binding: Binding) = Unit
                override fun visitAssistedInjectFactory(binding: AssistedInjectFactoryBinding) {
                    localAssistedInjectFactories += binding.model
                }
            })
        }

        // Add all condition literals from all local bindings.
        for (binding in localBindings.keys) {
            if (options.allConditionsLazy) {
                for (condition in binding.dependenciesOnConditions) {
                    localConditionLiterals[condition] = LiteralUsage.Lazy
                }
                continue
            }

            var isEager = true
            for (condition in binding.dependenciesOnConditions) {
                if (isEager) {
                    localConditionLiterals[condition] = when {
                        condition.requiresInstance -> LiteralUsage.Lazy  // Always lazy
                        else -> LiteralUsage.Eager
                    }
                    isEager = false
                } else {
                    if (condition !in localConditionLiterals) {
                        localConditionLiterals[condition] = LiteralUsage.Lazy
                    }
                }
            }
        }
        if (isRoot) {
            // Start from the root and go down to each child.
            distributeLocalConditionsAndFactories()

            // Propagate used parents up the hierarchy to close the gaps.
            for (child in childrenSequence(includeThis = false).toList().asReversed()) {
                (child as BindingGraphImpl).parent?.let { parent ->
                    // Add every used parent to the parent (except itself, if present)
                    parent.usedParents += child.usedParents - parent
                }
            }
        }
    }

    private fun distributeLocalConditionsAndFactories() {
        // Remove all local condition literals/assisted inject factories from every child (in-depth).
        for (child in childrenSequence(includeThis = false)) {
            child as BindingGraphImpl
            val usesConditions = child.localConditionLiterals.keys.removeAll(localConditionLiterals.keys)
            val usesAssistedInjectFactories = child.localAssistedInjectFactories.removeAll(localAssistedInjectFactories)
            if (usesConditions || usesAssistedInjectFactories) {
                // This will never be seen by materialization and that's okay, because no bindings are required here.
                child.usedParents += this
            }

            child.distributeLocalConditionsAndFactories()
        }
    }

    private fun materialize(dependency: NodeDependency): BaseBinding {
        return materializeLocal(dependency)
            ?: materializeInParents(dependency, BindingGraphImpl::materializeLocal)
            ?: materializeImplicit(dependency)
            ?: materializeInParents(dependency, BindingGraphImpl::materializeImplicit)
            ?: materializeMissing(dependency)
    }

    private fun materializeMissing(dependency: NodeDependency): Binding {
        return bindings.materializeMissing(dependency.node)
    }

    private fun materializeLocal(dependency: NodeDependency): BaseBinding? {
        return bindings.getExplicitBindingFor(dependency.node)
    }

    private fun materializeImplicit(dependency: NodeDependency): Binding? {
        return bindings.materializeImplicitBindingFor(dependency.node)
    }

    private fun materializeInParents(
        dependency: NodeDependency,
        materializeFunction: BindingGraphImpl.(NodeDependency) -> BaseBinding?,
    ): BaseBinding? {
        if (parent == null) return null
        val binding = parent.materializeFunction(dependency)
        if (binding != null) {
            // The binding is requested from a parent, so add parent to dependencies.
            usedParents += parent
            parent.materializationQueue += dependency
            return binding
        }
        return parent.materializeInParents(dependency, materializeFunction)?.also {
            usedParents += it.owner
        }
    }

    override fun toString(childContext: MayBeInvalid?) = modelRepresentation(
        modelClassName = "graph for",
        representation = component,
    )

    override fun validate(validator: Validator) {
        validator.inline(component)
        validator.inline(bindings)
        validator.child(variant)
        children.forEach(validator::child)

        if (factoryMethodInParent != null && component.factory != null) {
            validator.reportError(Strings.Errors.conflictingExplicitCreatorAndFactoryMethod()) {
                addNote(Strings.Notes.factoryMethodDeclaredHere(factory = factoryMethodInParent))
            }
        } else if (creator == null && !isRoot) {
            if (dependencies.isNotEmpty()) {
                validator.reportError(Strings.Errors.missingCreatorForDependencies())
            }

            if (modules.any { it.requiresInstance && !it.isTriviallyConstructable }) {
                validator.reportError(Strings.Errors.missingCreatorForModules()) {
                    modules.filter {
                        it.requiresInstance && !it.isTriviallyConstructable
                    }.forEach { module ->
                        addNote(Strings.Notes.missingModuleInstance(module))
                    }
                }
            }
        }

        // Validate every used binding in a graph structure.

        // Reachable via entry-points.
        entryPoints.forEach(validator::child)
        // Reachable via members-inject.
        memberInjectors.forEach(validator::child)
        // Reachable via component constructor instrumentation.
        instrumentedEntryPoints.map(::resolveBindingRaw).forEach(validator::child)

        subComponentFactoryMethods.forEach(validator::child)

        // Check component root status
        if (parent != null && isRoot) {
            validator.reportError(Strings.Errors.rootAsChild())
        }

        val parents = parentsSequence()

        // Check MT status is set explicitly
        if (component.requiresSynchronizedAccess) {
            parents.filter {
                !(it as BindingGraphImpl).component.requiresSynchronizedAccess
            }.forEach { parent ->
                validator.reportError(Strings.Errors.multiThreadStatusMismatch(parent = parent))
            }
        }

        // Check for duplicate scopes
        scopes.forEach { scope ->
            parents.find { parent -> scope in parent.scopes }?.let { withDuplicateScope ->
                validator.reportError(Strings.Errors.duplicateComponentScope(scope)) {
                    addNote(Strings.Notes.duplicateScopeComponent(component = withDuplicateScope))
                    addNote(Strings.Notes.duplicateScopeComponent(component = this@BindingGraphImpl))
                }
            }
        }

        if (ScopeModel.Reusable in scopes) {
            validator.reportError(Strings.Errors.reusableScopeOnComponent())
        }

        // Report hierarchy loops
        if (parents.find { parent -> parent.model == model } != null) {
            validator.reportError(Strings.Errors.componentLoop())
        }

        validateNoLoops(this, validator)
        validateAnnotationsRetention(this, validator)
    }
}
