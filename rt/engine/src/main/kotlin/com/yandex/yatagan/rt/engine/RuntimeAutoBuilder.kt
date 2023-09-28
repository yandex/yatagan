/*
 * Copyright 2023 Yandex LLC
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

import com.yandex.yatagan.AutoBuilder
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.model.ComponentDependencyModel
import com.yandex.yatagan.core.model.ModuleModel
import com.yandex.yatagan.internal.YataganInternal
import com.yandex.yatagan.internal.reportMissingAutoBuilderInput
import com.yandex.yatagan.internal.reportUnexpectedAutoBuilderInput
import com.yandex.yatagan.lang.rt.rt
import com.yandex.yatagan.rt.support.DynamicValidationDelegate
import com.yandex.yatagan.rt.support.Logger
import java.lang.reflect.Proxy
import kotlin.system.measureTimeMillis

internal class RuntimeAutoBuilder<T>(
    private val componentClass: Class<T>,
    private val graph: BindingGraph,
    private val validationPromise: DynamicValidationDelegate.Promise?,
    private val logger: () -> Logger?,
) : AutoBuilder<T> {
    init {
        assert(componentClass == graph.model.type.declaration.rt)
    }

    private val dependenciesByClass = graph.dependencies
        .associateBy { it.type.declaration.rt }

    private val modulesByClass = graph.modules
        .filter { it.requiresInstance }
        .associateBy { it.type.declaration.rt }

    private val givenDependencies = hashMapOf<ComponentDependencyModel, Any>()
    private val givenModuleInstances = hashMapOf<ModuleModel, Any>()

    @OptIn(YataganInternal::class)
    override fun <I : Any> provideInput(input: I, clazz: Class<I>): AutoBuilder<T> = apply {
        dependenciesByClass[clazz]?.let { dependency ->
            givenDependencies[dependency] = input
            return@apply
        }
        modulesByClass[clazz]?.let { module ->
            givenModuleInstances[module] = input
            return@apply
        }
        reportUnexpectedAutoBuilderInput(clazz, dependenciesByClass.keys + modulesByClass.keys)
    }

    @OptIn(YataganInternal::class)
    override fun create(): T {
        for ((clazz, dependency) in dependenciesByClass) {
            if (dependency !in givenDependencies) {
                reportMissingAutoBuilderInput(clazz)
            }
        }
        for ((clazz, module) in modulesByClass) {
            if (module.isTriviallyConstructable) {
                // Instance may or may not be present - it's okay
                continue
            }
            if (module !in givenModuleInstances) {
                reportMissingAutoBuilderInput(clazz)
            }
        }
        return componentClass.cast(createComponent())
    }

    private fun createComponent(): Any {
        val componentProxy: Any
        val time = measureTimeMillis {
            val componentClass = graph.model.type.declaration.rt
            val runtimeComponent = validationPromise.awaitOnError {
                RuntimeComponent(
                    logger = logger,
                    graph = graph,
                    parent = null,
                    givenInstances = emptyMap(),
                    givenDependencies = givenDependencies,
                    givenModuleInstances = givenModuleInstances,
                    validationPromise = validationPromise,
                )
            }
            componentProxy = Proxy.newProxyInstance(
                componentClass.classLoader,
                arrayOf(componentClass),
                runtimeComponent
            ).also {
                runtimeComponent.thisProxy = it
            }
        }
        logger()?.log(
            "Dynamic component creation via auto-builder of ${graph.toString(childContext = null)} took $time ms")
        return componentProxy
    }

}