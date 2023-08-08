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

package com.yandex.yatagan.rt.engine

import com.yandex.yatagan.base.memoize
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.GraphMemberInjector
import com.yandex.yatagan.core.graph.GraphSubComponentFactoryMethod
import com.yandex.yatagan.core.graph.WithParents
import com.yandex.yatagan.core.graph.bindings.AlternativesBinding
import com.yandex.yatagan.core.graph.bindings.AssistedInjectFactoryBinding
import com.yandex.yatagan.core.graph.bindings.Binding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyBinding
import com.yandex.yatagan.core.graph.bindings.ComponentDependencyEntryPointBinding
import com.yandex.yatagan.core.graph.bindings.ComponentInstanceBinding
import com.yandex.yatagan.core.graph.bindings.EmptyBinding
import com.yandex.yatagan.core.graph.bindings.InstanceBinding
import com.yandex.yatagan.core.graph.bindings.MapBinding
import com.yandex.yatagan.core.graph.bindings.MultiBinding
import com.yandex.yatagan.core.graph.bindings.ProvisionBinding
import com.yandex.yatagan.core.graph.bindings.SubComponentBinding
import com.yandex.yatagan.core.graph.component1
import com.yandex.yatagan.core.graph.component2
import com.yandex.yatagan.core.graph.parentsSequence
import com.yandex.yatagan.core.model.BooleanExpression
import com.yandex.yatagan.core.model.CollectionTargetKind
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ConditionModel
import com.yandex.yatagan.core.model.ConditionScope
import com.yandex.yatagan.core.model.DependencyKind
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeDependency
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.ScopeModel
import com.yandex.yatagan.core.model.component1
import com.yandex.yatagan.core.model.component2
import com.yandex.yatagan.lang.Callable
import com.yandex.yatagan.lang.Constructor
import com.yandex.yatagan.lang.Field
import com.yandex.yatagan.lang.Member
import com.yandex.yatagan.lang.Method
import com.yandex.yatagan.lang.isKotlinObject
import com.yandex.yatagan.lang.rt.kotlinObjectInstanceOrNull
import com.yandex.yatagan.lang.rt.rawValue
import com.yandex.yatagan.lang.rt.rt
import com.yandex.yatagan.rt.support.DynamicValidationDelegate
import com.yandex.yatagan.rt.support.Logger
import java.lang.reflect.Proxy

internal class RuntimeComponent(
    private val logger: Logger?,
    override val parent: RuntimeComponent?,
    private val graph: BindingGraph,
    private val givenInstances: Map<NodeModel, Any>,
    private val givenDependencies: Map<ComponentDependencyModel, Any>,
    validationPromise: DynamicValidationDelegate.Promise?,
    givenModuleInstances: Map<ModuleModel, Any>,
) : InvocationHandlerBase(validationPromise), Binding.Visitor<Any>,
    ConditionalAccessStrategy.ScopeEvaluator, WithParents<RuntimeComponent> {
    lateinit var thisProxy: Any
    private val parentsSequence = parentsSequence(includeThis = true).memoize()

    private val accessStrategies: Map<Binding, AccessStrategy> = buildMap(capacity = graph.localBindings.size) {
        val graphRequiresSynchronizedAccess = graph.requiresSynchronizedAccess
        for ((binding: Binding, usage) in graph.localBindings) {
            val requiresSynchronizedAccess = graphRequiresSynchronizedAccess && ScopeModel.Reusable !in binding.scopes
            val strategy = run {
                val provision: AccessStrategy = if (binding.scopes.isNotEmpty()) {
                    if (requiresSynchronizedAccess) {
                        SynchronizedCachingAccessStrategy(
                            binding = binding,
                            creationVisitor = this@RuntimeComponent,
                        )
                    } else {
                        CachingAccessStrategy(
                            binding = binding,
                            creationVisitor = this@RuntimeComponent,
                        )
                    }
                } else {
                    if (requiresSynchronizedAccess) {
                        SynchronizedCreatingAccessStrategy(
                            binding = binding,
                            creationVisitor = this@RuntimeComponent,
                        )
                    } else {
                        CreatingAccessStrategy(
                            binding = binding,
                            creationVisitor = this@RuntimeComponent,
                        )
                    }
                }
                if (usage.hasOptionalUsage()) {
                    ConditionalAccessStrategy(
                        underlying = provision,
                        evaluator = this@RuntimeComponent,
                        conditionScopeHolder = binding,
                    )
                } else provision
            }
            put(binding, strategy)
        }
    }

    private val conditionLiterals = HashMap<ConditionModel, Boolean>(graph.localConditionLiterals.size, 1.0f).apply {
        for ((literal, usage) in graph.localConditionLiterals) {
            when (usage) {
                BindingGraph.LiteralUsage.Eager -> {
                    put(literal, doEvaluateLiteral(literal))
                }
                BindingGraph.LiteralUsage.Lazy -> {
                    // To be computed on demand.
                }
            }
        }
    }

    private val moduleInstances = buildMap<ModuleModel, Any> {
        putAll(givenModuleInstances)
        for (module in graph.modules) {
            if (module.requiresInstance && module.isTriviallyConstructable && module !in givenModuleInstances) {
                put(module, module.type.declaration.rt.getConstructor().newInstance())
            }
        }
    }

    private val booleanExpressionEvaluator = object : BooleanExpression.Visitor<Boolean> {
        override fun visitVariable(variable: BooleanExpression.Variable): Boolean {
            val model = variable.model
            return parentsSequence
                .first { model in it.graph.localConditionLiterals }
                .conditionLiterals.getOrPut(model) {
                    doEvaluateLiteral(model)
                }
        }

        override fun visitAnd(and: BooleanExpression.And): Boolean {
            return and.lhs.accept(this) && and.rhs.accept(this)
        }

        override fun visitOr(or: BooleanExpression.Or): Boolean {
            return or.lhs.accept(this) || or.rhs.accept(this)
        }

        override fun visitNot(not: BooleanExpression.Not): Boolean = !not.underlying.accept(this)
    }

    init {
        for ((getter, dependency) in graph.entryPoints) {
            implementMethod(getter.rt, EntryPointHandler(dependency))
        }
        for (memberInject in graph.memberInjectors) {
            implementMethod(memberInject.injector.rt, MemberInjectorHandler(memberInject))
        }
        for (factory in graph.subComponentFactoryMethods) {
            implementMethod(factory.model.factoryMethod.rt, ChildComponentFactoryHandler(factory))
        }
    }

    fun resolveAndAccess(dependency: NodeDependency): Any {
        val (node, kind) = dependency
        val binding = graph.resolveBinding(node)
        return componentForGraph(binding.owner).access(binding, kind)
    }

    private fun resolveAndAccessIfCondition(dependency: NodeDependency): Any? {
        val (node, kind) = dependency
        val binding = graph.resolveBinding(node)
        return if (evaluateConditionScope(binding.conditionScope)) {
            componentForGraph(binding.owner).access(binding, kind)
        } else null
    }

    private fun componentForGraph(graph: BindingGraph): RuntimeComponent {
        return parentsSequence.first { it.graph == graph }
    }

    private fun access(binding: Binding, kind: DependencyKind): Any {
        with(accessStrategies[binding]!!) {
            return when (kind) {
                DependencyKind.Direct -> get()
                DependencyKind.Lazy -> getLazy()
                DependencyKind.Provider -> getProvider()
                DependencyKind.Optional -> getOptional()
                DependencyKind.OptionalLazy -> getOptionalLazy()
                DependencyKind.OptionalProvider -> getOptionalProvider()
            }
        }
    }

    private fun doEvaluateLiteral(literal: ConditionModel): Boolean {
        var instance: Any? = when {
            literal.requiresInstance -> resolveAndAccess(literal.root)
            else -> null
        }
        for (member in literal.path) {
            instance = member.accept(MemberEvaluator(instance))
        }
        return instance as Boolean
    }

    override fun evaluateConditionScope(conditionScope: ConditionScope): Boolean {
        return when (conditionScope) {
            ConditionScope.Always -> true
            ConditionScope.Never -> false
            is ConditionScope.ExpressionScope -> conditionScope.expression.accept(booleanExpressionEvaluator)
        }
    }

    override fun toString(): String = graph.toString(childContext = null).toString()

    override fun visitProvision(binding: ProvisionBinding): Any {
        val instance: Any? = binding.provision.accept(ProvisionEvaluator(binding))
        return checkNotNull(instance) {
            "Binding $binding yielded null result"
        }
    }

    override fun visitAssistedInjectFactory(binding: AssistedInjectFactoryBinding): Any {
        val model = binding.model
        return Proxy.newProxyInstance(
            javaClass.classLoader, arrayOf(model.type.declaration.rt),
            RuntimeAssistedInjectFactory(
                model = model,
                owner = this,
                validationPromise = validationPromise,
            )
        )
    }

    override fun visitInstance(binding: InstanceBinding): Any {
        return checkNotNull(givenInstances[binding.target]) {
            "Provided instance for ${binding.target.toString(null)} is null"
        }
    }

    override fun visitAlternatives(binding: AlternativesBinding): Any {
        binding.alternatives.forEachIndexed { index, alternative: NodeModel ->
            if (index == binding.alternatives.lastIndex) {
                // No need to check condition for the last alternative.
                // If its condition doesn't hold, then the graph is invalid. So we're in the area of UB.
                return resolveAndAccess(alternative)
            } else {
                resolveAndAccessIfCondition(alternative)?.let { return it }
            }
        }
        throw AssertionError("Not reached")
    }

    override fun visitSubComponent(binding: SubComponentBinding): Any {
        val creator = binding.targetGraph.model.factory
        val clazz = (creator ?: binding.targetGraph.model).type.declaration.rt
        return Proxy.newProxyInstance(
            clazz.classLoader,
            arrayOf(clazz),
            if (creator != null) RuntimeFactory(
                logger = logger,
                graph = binding.targetGraph,
                parent = this@RuntimeComponent,
                validationPromise = validationPromise,  // The same validation session for children.
            ) else RuntimeComponent(
                logger = logger,
                parent = this@RuntimeComponent,
                graph = binding.targetGraph,
                givenInstances = emptyMap(),
                givenDependencies = emptyMap(),
                givenModuleInstances = emptyMap(),
                validationPromise = validationPromise,
            )
        )
    }

    override fun visitComponentDependency(binding: ComponentDependencyBinding): Any {
        return checkNotNull(givenDependencies[binding.dependency]) {
            "Provided instance for dependency ${binding.dependency.toString(null)} is null"
        }
    }

    override fun visitComponentInstance(binding: ComponentInstanceBinding): Any {
        return componentForGraph(binding.owner).thisProxy
    }

    override fun visitComponentDependencyEntryPoint(binding: ComponentDependencyEntryPointBinding): Any {
        return binding.getter.rt.invoke(checkNotNull(givenDependencies[binding.dependency]) {
            "Provided instance for dependency ${binding.dependency.toString(null)} is null"
        })
    }

    override fun visitMulti(binding: MultiBinding): Any {
        val collection: MutableCollection<Any?> = when (binding.kind) {
            CollectionTargetKind.List -> arrayListOf()
            CollectionTargetKind.Set -> hashSetOf()
        }
        binding.upstream?.let { upstream ->
            collection.addAll(componentForGraph(upstream.owner)
                .access(upstream, DependencyKind.Direct) as Collection<*>)
        }
        for ((node: NodeModel, kind: MultiBinding.ContributionType) in binding.contributions) {
            resolveAndAccessIfCondition(node)?.let { contribution ->
                when (kind) {
                    MultiBinding.ContributionType.Element -> collection.add(contribution)
                    MultiBinding.ContributionType.Collection -> collection.addAll(contribution as Collection<*>)
                }
            }
        }
        return collection
    }

    override fun visitMap(binding: MapBinding): Any {
        return buildMap(capacity = binding.contents.size) {
            binding.upstream?.let { upstream ->
                putAll(componentForGraph(upstream.owner).access(upstream, DependencyKind.Direct) as Map<*, *>)
            }
            for (contributionEntry in binding.contents) {
                resolveAndAccessIfCondition(contributionEntry.dependency)?.let { contribution ->
                    put(contributionEntry.keyValue.rawValue, contribution)
                }
            }
        }
    }

    private class MemberEvaluator(private val instance: Any?) : Member.Visitor<Any?> {
        override fun visitMethod(model: Method): Any? = model.rt.invoke(instance)
        override fun visitField(model: Field): Any? = model.rt.get(instance)
    }

    private inner class ProvisionEvaluator(val binding: ProvisionBinding) : Callable.Visitor<Any?> {
        private fun args(): Array<Any> = binding.inputs.let { inputs ->
            Array(inputs.size) { index ->
                resolveAndAccess(inputs[index])
            }
        }

        override fun visitMethod(method: Method): Any? = method.rt.invoke(/*receiver*/ when {
            binding.requiresModuleInstance -> {
                val module = binding.originModule!!
                checkNotNull(moduleInstances[module]) {
                    "Provided module instance for $module is null"
                }
            }
            method.owner.isKotlinObject -> {
                method.owner.kotlinObjectInstanceOrNull()
            }
            else -> null
        }, /* method arguments*/ *args())

        override fun visitConstructor(constructor: Constructor): Any? = constructor.rt.newInstance(*args())
    }

    override fun visitEmpty(binding: EmptyBinding): Any {
        throw IllegalStateException(
            "Missing binding encountered in `${graph.toString(null)}`: ${binding.toString(null)}")
    }

    private inner class EntryPointHandler(val dependency: NodeDependency) : MethodHandler {
        override fun invoke(proxy: Any, args: Array<Any?>?): Any {
            return resolveAndAccess(dependency)
        }
    }

    private inner class MemberInjectorHandler(val memberInject: GraphMemberInjector) : MethodHandler {
        override fun invoke(proxy: Any, args: Array<Any?>?): Any? {
            val (injectee) = args!!
            for ((member, dependency) in memberInject.membersToInject) {
                val value = resolveAndAccess(dependency)
                member.accept(object : Member.Visitor<Unit> {
                    override fun visitField(model: Field) = model.rt.set(injectee, value)
                    override fun visitMethod(model: Method) {
                        model.rt.invoke(injectee, value)
                    }
                })
            }
            return null
        }
    }

    private inner class ChildComponentFactoryHandler(
        val factory: GraphSubComponentFactoryMethod,
    ) : MethodHandler {
        override fun invoke(proxy: Any, args: Array<Any?>?): Any? {
            val givenInstances = hashMapOf<NodeModel, Any>()
            val givenDependencies = hashMapOf<ComponentDependencyModel, Any>()
            val givenModuleInstances = hashMapOf<ModuleModel, Any>()
            fun consumePayload(payload: ComponentFactoryModel.InputPayload, arg: Any) {
                when (payload) {
                    is ComponentFactoryModel.InputPayload.Dependency ->
                        givenDependencies[payload.model] = arg
                    is ComponentFactoryModel.InputPayload.Instance ->
                        givenInstances[payload.model] = arg
                    is ComponentFactoryModel.InputPayload.Module ->
                        givenModuleInstances[payload.model] = arg
                }
            }

            for ((input, arg) in factory.model.factoryInputs.zip(args!!)) {
                consumePayload(payload = input.payload, arg = arg!!)
            }

            val componentClass = factory.model.createdComponent.type.declaration.rt
            val runtimeComponent = RuntimeComponent(
                logger = logger,
                graph = checkNotNull(factory.createdGraph),
                parent = this@RuntimeComponent,
                givenInstances = givenInstances,
                givenDependencies = givenDependencies,
                givenModuleInstances = givenModuleInstances,
                validationPromise = validationPromise,
            )
            return Proxy.newProxyInstance(
                componentClass.classLoader,
                arrayOf(componentClass),
                runtimeComponent
            ).also {
                runtimeComponent.thisProxy = it
            }
        }
    }
}

private fun BindingGraph.BindingUsage.hasOptionalUsage(): Boolean {
    return (optional + optionalLazy + optionalProvider) > 0
}