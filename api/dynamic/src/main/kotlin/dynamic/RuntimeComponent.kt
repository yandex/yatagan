package com.yandex.daggerlite.dynamic

import com.yandex.daggerlite.DynamicValidationDelegate
import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.DependencyKind
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeDependency
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.lang.CallableLangModel
import com.yandex.daggerlite.core.lang.ConstructorLangModel
import com.yandex.daggerlite.core.lang.FieldLangModel
import com.yandex.daggerlite.core.lang.FunctionLangModel
import com.yandex.daggerlite.core.lang.MemberLangModel
import com.yandex.daggerlite.core.lang.isKotlinObject
import com.yandex.daggerlite.graph.AlternativesBinding
import com.yandex.daggerlite.graph.AssistedInjectFactoryBinding
import com.yandex.daggerlite.graph.Binding
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.ComponentDependencyBinding
import com.yandex.daggerlite.graph.ComponentDependencyEntryPointBinding
import com.yandex.daggerlite.graph.ComponentInstanceBinding
import com.yandex.daggerlite.graph.ConditionScope
import com.yandex.daggerlite.graph.ConditionScope.Literal
import com.yandex.daggerlite.graph.EmptyBinding
import com.yandex.daggerlite.graph.GraphMemberInjector
import com.yandex.daggerlite.graph.InstanceBinding
import com.yandex.daggerlite.graph.MultiBinding
import com.yandex.daggerlite.graph.ProvisionBinding
import com.yandex.daggerlite.graph.SubComponentFactoryBinding
import com.yandex.daggerlite.graph.component1
import com.yandex.daggerlite.graph.component2
import com.yandex.daggerlite.graph.normalized
import com.yandex.daggerlite.lang.rt.kotlinObjectInstanceOrNull
import com.yandex.daggerlite.lang.rt.rt
import java.lang.reflect.Proxy

internal class RuntimeComponent(
    private val graph: BindingGraph,
    private val parent: RuntimeComponent?,
    private val givenInstances: Map<NodeModel, Any>,
    private val givenDependencies: Map<ComponentDependencyModel, Any>,
    validationPromise: DynamicValidationDelegate.Promise?,
    givenModuleInstances: Map<ModuleModel, Any>,
) : InvocationHandlerBase(validationPromise), Binding.Visitor<Any> {
    lateinit var thisProxy: Any
    private val accessStrategies = hashMapOf<Binding, AccessStrategy>()
    private val conditionLiterals = HashMap<Literal, Boolean>().apply {
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

    fun resolveAndAccess(dependency: NodeDependency): Any {
        val (node, kind) = dependency
        val binding = graph.resolveBinding(node)
        return componentForGraph(binding.owner).access(binding, kind)
    }

    private fun resolveAndAccessIfCondition(node: NodeModel): Any? {
        val binding = graph.resolveBinding(node)
        return if (evaluateConditionScope(binding.conditionScope)) {
            componentForGraph(binding.owner).access(binding, DependencyKind.Direct)
        } else null
    }

    private fun componentForGraph(graph: BindingGraph): RuntimeComponent {
        return if (this.graph == graph) this else parent!!.componentForGraph(graph)
    }

    private fun access(binding: Binding, kind: DependencyKind): Any {
        with(accessStrategies[binding]!!) {
            return when (kind) {
                DependencyKind.Direct -> getDirect()
                DependencyKind.Lazy -> getLazy()
                DependencyKind.Provider -> getProvider()
                DependencyKind.Optional -> getOptional()
                DependencyKind.OptionalLazy -> getOptionalLazy()
                DependencyKind.OptionalProvider -> getOptionalProvider()
            }
        }
    }

    private fun doEvaluateLiteral(literal: Literal): Boolean {
        var instance: Any? = null
        for (member in literal.path) {
            instance = member.accept(MemberEvaluator(instance))
        }
        return instance as Boolean
    }

    private fun evaluateLiteral(literal: Literal): Boolean {
        val normalized = literal.normalized()
        return if (normalized in graph.localConditionLiterals) {
            conditionLiterals.getOrPut(normalized) {
                doEvaluateLiteral(normalized)
            } xor literal.negated
        } else {
            checkNotNull(parent) {
                "Not reached: unexpected literal $literal"
            }.evaluateLiteral(literal)
        }
    }

    private fun evaluateConditionScope(conditionScope: ConditionScope): Boolean {
        for (clause in conditionScope.expression) {
            var clauseValue = false
            for (literal in clause) clauseValue = clauseValue || evaluateLiteral(literal)
            if (!clauseValue) return false
        }
        return true
    }

    private fun evaluate(binding: Binding): Any {
        return validationPromise.awaitOnError {
            binding.accept(this)
        }
    }

    init {
        for ((binding: Binding, _) in graph.localBindings) {
            val creation: () -> Any = { evaluate(binding) }
            accessStrategies[binding] = run {
                val provision: AccessStrategy = if (binding.scope != null) {
                    CachingAccessStrategy(creation)
                } else {
                    CreatingAccessStrategy(creation)
                }
                if (!binding.conditionScope.isAlways) {
                    ConditionalAccessStrategy(
                        underlying = provision,
                        isPresent = { evaluateConditionScope(binding.conditionScope) },
                    )
                } else provision
            }
        }

        for ((getter, dependency) in graph.entryPoints) {
            implementMethod(getter.rt, EntryPointHandler(dependency))
        }
        for (memberInject in graph.memberInjectors) {
            implementMethod(memberInject.injector.rt, MemberInjectorHandler(memberInject))
        }
    }

    override fun toString(): String = graph.toString()

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
            "Provided instance for ${binding.target} is null"
        }
    }

    override fun visitAlternatives(binding: AlternativesBinding): Any {
        for (alternative: NodeModel in binding.alternatives) {
            resolveAndAccessIfCondition(alternative)?.let {
                return it
            }
        }
        throw AssertionError("Not reached: inconsistent condition")
    }

    override fun visitSubComponentFactory(binding: SubComponentFactoryBinding): Any {
        val creatorClass = checkNotNull(binding.targetGraph.creator) {
            "No creator is declared in ${binding.targetGraph}"
        }.type.declaration.rt
        return Proxy.newProxyInstance(
            creatorClass.classLoader,
            arrayOf(creatorClass),
            RuntimeFactory(
                graph = binding.targetGraph,
                parent = this@RuntimeComponent,
                validationPromise = validationPromise,  // The same validation session for children.
            )
        )
    }

    override fun visitComponentDependency(binding: ComponentDependencyBinding): Any {
        return checkNotNull(givenDependencies[binding.dependency]) {
            "Provided instance for dependency ${binding.dependency} is null"
        }
    }

    override fun visitComponentInstance(binding: ComponentInstanceBinding): Any {
        return componentForGraph(binding.owner).thisProxy
    }

    override fun visitComponentDependencyEntryPoint(binding: ComponentDependencyEntryPointBinding): Any {
        return binding.getter.rt.invoke(checkNotNull(givenDependencies[binding.dependency]) {
            "Provided instance for dependency ${binding.dependency} is null"
        })
    }

    override fun visitMulti(binding: MultiBinding): Any {
        return buildList(capacity = binding.contributions.size) {
            for ((node: NodeModel, kind: MultiBinding.ContributionType) in binding.contributions) {
                resolveAndAccessIfCondition(node)?.let { contribution ->
                    when (kind) {
                        MultiBinding.ContributionType.Element -> add(contribution)
                        MultiBinding.ContributionType.Collection -> addAll(contribution as Collection<*>)
                    }
                }
            }
        }
    }

    private class MemberEvaluator(private val instance: Any?) : MemberLangModel.Visitor<Any?> {
        override fun visitFunction(model: FunctionLangModel): Any? = model.rt.invoke(instance)
        override fun visitField(model: FieldLangModel): Any? = model.rt.get(instance)
    }

    private inner class ProvisionEvaluator(val binding: ProvisionBinding) : CallableLangModel.Visitor<Any?> {
        private fun args(): Array<Any> = binding.inputs.let { inputs ->
            Array(inputs.size) { index ->
                resolveAndAccess(inputs[index])
            }
        }

        override fun visitFunction(function: FunctionLangModel): Any? = function.rt.invoke(/*receiver*/ when {
            binding.requiresModuleInstance -> {
                val module = binding.originModule!!
                checkNotNull(moduleInstances[module]) {
                    "Provided module instance for $module is null"
                }
            }
            function.owner.isKotlinObject -> {
                function.owner.kotlinObjectInstanceOrNull()
            }
            else -> null
        }, /* function arguments*/ *args())

        override fun visitConstructor(constructor: ConstructorLangModel): Any? = constructor.rt.newInstance(*args())
    }

    override fun visitEmpty(binding: EmptyBinding): Any {
        throw IllegalStateException("Missing binding encountered in `$graph`: $binding")
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
                member.accept(object : MemberLangModel.Visitor<Unit> {
                    override fun visitField(model: FieldLangModel) = model.rt.set(injectee, value)
                    override fun visitFunction(model: FunctionLangModel) {
                        model.rt.invoke(injectee, value)
                    }
                })
            }
            return null
        }
    }
}
