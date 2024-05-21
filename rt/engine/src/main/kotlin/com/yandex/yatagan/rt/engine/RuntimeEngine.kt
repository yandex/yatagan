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

import com.yandex.yatagan.AutoBuilder
import com.yandex.yatagan.Component
import com.yandex.yatagan.base.loadServices
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.impl.BindingGraph
import com.yandex.yatagan.core.graph.impl.Options
import com.yandex.yatagan.core.model.impl.ComponentModel
import com.yandex.yatagan.lang.common.LangOptions
import com.yandex.yatagan.lang.rt.RtLexicalScope
import com.yandex.yatagan.rt.support.DynamicValidationDelegate
import com.yandex.yatagan.rt.support.Logger
import com.yandex.yatagan.rt.support.SimpleDynamicValidationDelegate
import com.yandex.yatagan.validation.LocatedMessage
import com.yandex.yatagan.validation.RichString
import com.yandex.yatagan.validation.ValidationMessage
import com.yandex.yatagan.validation.format.format
import com.yandex.yatagan.validation.impl.GraphValidationExtension
import com.yandex.yatagan.validation.impl.validate
import com.yandex.yatagan.validation.spi.ValidationPluginProvider
import java.lang.ref.SoftReference
import java.lang.reflect.Proxy
import java.util.concurrent.Future
import kotlin.system.measureTimeMillis

/**
 * Main entrypoint class for reflection backend implementation.
 */
class RuntimeEngine(
    private val paramsFuture: Future<Params>,
) {
    private val params: Params
        get() = paramsFuture.get()

    private val logger: Logger?
        get() = paramsFuture.get().validationDelegate.logger

    data class Params(
        var validationDelegate: DynamicValidationDelegate = SimpleDynamicValidationDelegate(),
        var maxIssueEncounterPaths: Int = 5,
        var isStrictMode: Boolean = true,
        var usePlainOutput: Boolean = false,
        var allConditionsLazy: Boolean = false,
    )

    private val lexicalScopeCache = hashMapOf<ClassLoader, SoftReference<RtLexicalScope>>()

    private fun obtainLexicalScopeFor(clazz: Class<*>): RtLexicalScope = synchronized(lexicalScopeCache) {
        val classLoader = clazz.classLoader
        return lexicalScopeCache[classLoader]?.get() ?: run {
            RtLexicalScope(classLoader).also {
                it.ext[Options] = Options(
                    allConditionsLazy = params.allConditionsLazy,
                )
                it.ext[LangOptions] = LangOptions(
                    // TODO: Support compat mode in RT
                    daggerCompatibilityMode = false,
                )
                lexicalScopeCache[classLoader] = SoftReference(it)
            }
        }
    }

    fun <T : Any> builder(builderClass: Class<T>): T {
        val builder: T
        val time = measureTimeMillis {
            require(builderClass.isAnnotationPresent(Component.Builder::class.java)) {
                "$builderClass is not a builder for a Yatagan component"
            }

            val lexicalScope = obtainLexicalScopeFor(builderClass)
            val builderDeclaration = lexicalScope.getTypeDeclaration(builderClass)
            val componentClass = requireNotNull(builderDeclaration.enclosingType) {
                "No enclosing component class found for $builderClass"
            }
            val componentModel = ComponentModel(componentClass)
            require(componentModel.isRoot) {
                "$componentClass is not a root Yatagan component"
            }
            val graph = BindingGraph(
                root = componentModel,
            )
            val promise = doValidate(graph)
            val factory = promise.awaitOnError {
                RuntimeFactory(
                    graph = graph,
                    parent = null,
                    validationPromise = promise,
                    logger = this::logger,
                )
            }
            val proxy = Proxy.newProxyInstance(builderClass.classLoader, arrayOf(builderClass), factory)
            builder = builderClass.cast(proxy)
        }
        logger?.log("Dynamic builder creation for `$builderClass` took $time ms")
        return builder
    }

    fun <T : Any> autoBuilder(componentClass: Class<T>): AutoBuilder<T> {
        val builder: AutoBuilder<T>
        val time = measureTimeMillis {
            require(componentClass.getAnnotation(Component::class.java)?.isRoot == true) {
                "$componentClass is not a root Yatagan component"
            }

            val lexicalScope = obtainLexicalScopeFor(componentClass)
            val componentModel = ComponentModel(lexicalScope.getTypeDeclaration(componentClass))
            if (componentModel.factory != null) {
                throw IllegalArgumentException(
                    "Auto-builder can't be used for $componentClass, because it declares an explicit builder. " +
                            "Please use `Yatagan.builder()` instead"
                )
            }

            val graph = BindingGraph(
                root = componentModel,
            )
            val promise = doValidate(graph)
            builder = RuntimeAutoBuilder(
                componentClass = componentClass,
                graph = graph,
                validationPromise = promise,
                logger = this::logger,
            )
        }
        logger?.log("Dynamic auto-builder creation for `$componentClass` took $time ms")
        return builder
    }

    private fun reportMessages(
        messages: Collection<LocatedMessage>,
        reporting: DynamicValidationDelegate.ReportingDelegate,
    ) {
        fun toString(rich: RichString) = if (params.usePlainOutput) rich.toString() else rich.toAnsiEscapedString()

        messages.forEach { locatedMessage ->
            val text: RichString = locatedMessage.format(
                maxEncounterPaths = params.maxIssueEncounterPaths,
            )
            when (locatedMessage.message.kind) {
                ValidationMessage.Kind.Error -> reporting.reportError(toString(text))
                ValidationMessage.Kind.Warning -> reporting.reportWarning(toString(text))
                ValidationMessage.Kind.MandatoryWarning -> if (params.isStrictMode) {
                    reporting.reportError(toString(text))
                } else {
                    reporting.reportWarning(toString(text))
                }
            }
        }
    }

    private fun doValidate(graph: BindingGraph) = params.validationDelegate.let { delegate ->
        delegate.dispatchValidation(title = graph.model.type.toString()) { reporting ->
            reportMessages(messages = validate(graph), reporting = reporting)
            if (delegate.usePlugins) {
                val extension = GraphValidationExtension(
                    validationPluginProviders = pluginProviders,
                    graph = graph,
                )
                reportMessages(messages = validate(extension), reporting = reporting)
            }
        }
    }

    private companion object {
        val pluginProviders: List<ValidationPluginProvider> = loadServices()
    }
}