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

import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ComponentFactoryModel
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.core.model.NodeModel
import com.yandex.yatagan.core.model.allInputs
import com.yandex.yatagan.lang.rt.rt
import com.yandex.yatagan.rt.support.DynamicValidationDelegate
import com.yandex.yatagan.rt.support.Logger
import java.lang.reflect.Proxy
import kotlin.system.measureTimeMillis

internal class RuntimeFactory(
    private val graph: BindingGraph,
    private val parent: RuntimeComponent?,
    validationPromise: DynamicValidationDelegate.Promise?,
    private val logger: Logger?,
) : InvocationHandlerBase(validationPromise), InvocationHandlerBase.MethodHandler {
    private val creator = checkNotNull(graph.model.factory) {
        "Component $graph has no explicit creator (builder/factory)"
    }

    private val givenInstances = hashMapOf<NodeModel, Any>()
    private val givenDependencies = hashMapOf<ComponentDependencyModel, Any>()
    private val givenModuleInstances = hashMapOf<ModuleModel, Any>()

    init {
        creator.factoryMethod?.let { factoryMethod ->
            implementMethod(factoryMethod.rt, this)
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

    override fun toString() = creator.toString(childContext = null).toString()

    override fun invoke(proxy: Any, args: Array<Any?>?): Any {
        val componentProxy: Any
        val time = measureTimeMillis {
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
                logger = logger,
                graph = graph,
                parent = parent,
                givenInstances = givenInstances,
                givenDependencies = givenDependencies,
                givenModuleInstances = givenModuleInstances,
                validationPromise = validationPromise,
            )
            componentProxy = Proxy.newProxyInstance(
                componentClass.classLoader,
                arrayOf(componentClass),
                runtimeComponent
            ).also {
                runtimeComponent.thisProxy = it
            }
        }
        logger?.log("Dynamic component creation via ${creator.toString(childContext = null)} took $time ms")
        return componentProxy
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
