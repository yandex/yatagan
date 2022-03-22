package com.yandex.daggerlite.dynamic

import com.yandex.daggerlite.core.ComponentDependencyModel
import com.yandex.daggerlite.core.ComponentFactoryModel
import com.yandex.daggerlite.core.ModuleModel
import com.yandex.daggerlite.core.NodeModel
import com.yandex.daggerlite.core.allInputs
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.lang.rt.rt
import java.lang.reflect.Proxy

internal class RuntimeFactory(
    private val graph: BindingGraph,
    private val parent: RuntimeComponent?,
) : InvocationHandlerBase() {
    private val creator = checkNotNull(graph.creator) {
        "Component $graph has no explicit creator (builder/factory)"
    }

    private val givenInstances = hashMapOf<NodeModel, Any>()
    private val givenDependencies = hashMapOf<ComponentDependencyModel, Any>()
    private val givenModuleInstances = hashMapOf<ModuleModel, Any>()

    init {
        creator.factoryMethod?.let { factoryMethod ->
            implementMethod(factoryMethod.rt, FactoryMethodHandler())
        }
        for (input in creator.builderInputs) {
            implementMethod(input.builderSetter.rt, BuilderSetterHandler(input))
        }
    }

    private fun consumePayload(payload: ComponentFactoryModel.InputPayload, arg: Any) {
        when (payload) {
            is ComponentFactoryModel.InputPayload.Dependency ->
                givenDependencies[payload.dependency] = arg
            is ComponentFactoryModel.InputPayload.Instance ->
                givenInstances[payload.node] = arg
            is ComponentFactoryModel.InputPayload.Module ->
                givenModuleInstances[payload.module] = arg
        }
    }

    override fun toString() = creator.toString()

    private inner class FactoryMethodHandler : MethodHandler {
        override fun invoke(proxy: Any, args: Array<Any?>?): Any {
            if (creator.factoryInputs.isNotEmpty()) {
                for ((input, arg) in creator.factoryInputs.zip(args!!)) {
                    consumePayload(payload = input.payload, arg = arg!!)
                }
            }
            for (input in creator.allInputs) {
                when (val payload = input.payload) {
                    is ComponentFactoryModel.InputPayload.Dependency -> check(payload.dependency in givenDependencies) {
                        "No value for $payload was provided"
                    }
                    is ComponentFactoryModel.InputPayload.Instance -> check(payload.node in givenInstances) {
                        "No value for $payload was provided"
                    }
                    is ComponentFactoryModel.InputPayload.Module -> check(payload.module in givenModuleInstances) {
                        "No value for $payload was provided"
                    }
                }
            }
            val componentClass = creator.createdComponent.type.declaration.rt
            val runtimeComponent = RuntimeComponent(
                graph = graph,
                parent = parent,
                givenInstances = givenInstances,
                givenDependencies = givenDependencies,
                givenModuleInstances = givenModuleInstances,
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

    private inner class BuilderSetterHandler(private val input: ComponentFactoryModel.InputModel) : MethodHandler {
        override fun invoke(proxy: Any, args: Array<Any?>?): Any {
            consumePayload(payload = input.payload, arg = checkNotNull(args!!.first()) {
                "Creator input `$input` is null"
            })
            return proxy
        }
    }
}
