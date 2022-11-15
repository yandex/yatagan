package com.yandex.yatagan.rt.engine

import com.yandex.yatagan.Component
import com.yandex.yatagan.base.ObjectCacheRegistry
import com.yandex.yatagan.base.loadServices
import com.yandex.yatagan.core.graph.BindingGraph
import com.yandex.yatagan.core.graph.impl.BindingGraph
import com.yandex.yatagan.core.model.impl.ComponentFactoryModel
import com.yandex.yatagan.core.model.impl.ComponentModel
import com.yandex.yatagan.lang.InternalLangApi
import com.yandex.yatagan.lang.LangModelFactory
import com.yandex.yatagan.lang.rt.RtModelFactoryImpl
import com.yandex.yatagan.lang.rt.TypeDeclaration
import com.yandex.yatagan.rt.support.DynamicValidationDelegate
import com.yandex.yatagan.rt.support.Logger
import com.yandex.yatagan.validation.LocatedMessage
import com.yandex.yatagan.validation.RichString
import com.yandex.yatagan.validation.ValidationMessage
import com.yandex.yatagan.validation.format.format
import com.yandex.yatagan.validation.impl.GraphValidationExtension
import com.yandex.yatagan.validation.impl.validate
import com.yandex.yatagan.validation.spi.ValidationPluginProvider
import java.lang.reflect.Proxy
import kotlin.system.measureTimeMillis

/**
 * Main entrypoint class for reflection backend implementation.
 */
class RuntimeEngine<P : RuntimeEngine.Params>(
    val params: P,
) {
    init {
        @OptIn(InternalLangApi::class)
        LangModelFactory.delegate = RtModelFactoryImpl(javaClass.classLoader)
    }

    interface Params {
        val validationDelegate: DynamicValidationDelegate?
        val maxIssueEncounterPaths: Int
        val isStrictMode: Boolean
        val logger: Logger?
    }

    fun destroy() {
        @OptIn(InternalLangApi::class)
        LangModelFactory.delegate = null
        ObjectCacheRegistry.close()
    }

    fun <T : Any> builder(builderClass: Class<T>): T {
        val builder: T
        val time = measureTimeMillis {
            require(builderClass.isAnnotationPresent(Component.Builder::class.java)) {
                "$builderClass is not a component builder"
            }

            val graph = BindingGraph(
                root = ComponentFactoryModel(TypeDeclaration(builderClass)).createdComponent
            )
            val promise = doValidate(graph)
            val factory = promise.awaitOnError {
                RuntimeFactory(
                    graph = graph,
                    parent = null,
                    validationPromise = promise,
                    logger = params.logger,
                )
            }
            val proxy = Proxy.newProxyInstance(builderClass.classLoader, arrayOf(builderClass), factory)
            builder = builderClass.cast(proxy)
        }
        params.logger?.log("Dynamic builder creation for `$builderClass` took $time ms")
        return builder
    }

    fun <T : Any> create(componentClass: Class<T>): T {
        val componentInstance: T
        val time = measureTimeMillis {
            require(componentClass.isAnnotationPresent(Component::class.java)) {
                "$componentClass is not a component"
            }

            val graph = BindingGraph(ComponentModel(TypeDeclaration(componentClass)))
            val promise = doValidate(graph)
            val component = promise.awaitOnError {
                RuntimeComponent(
                    logger = params.logger,
                    graph = graph,
                    parent = null,
                    givenDependencies = emptyMap(),
                    givenInstances = emptyMap(),
                    givenModuleInstances = emptyMap(),
                    validationPromise = promise,
                )
            }
            require(graph.creator == null) {
                "$componentClass has explicit creator interface declared, use `Yatagan.builder()` instead"
            }

            val proxy = Proxy.newProxyInstance(componentClass.classLoader, arrayOf(componentClass), component)
            component.thisProxy = proxy
            componentInstance = componentClass.cast(proxy)
        }
        params.logger?.log("Dynamic component creation for `$componentClass` took $time ms")
        return componentInstance
    }

    private fun reportMessages(
        messages: Collection<LocatedMessage>,
        reporting: DynamicValidationDelegate.ReportingDelegate,
    ) {
        messages.forEach { locatedMessage ->
            val text: RichString = locatedMessage.format(
                maxEncounterPaths = params.maxIssueEncounterPaths,
            )
            when (locatedMessage.message.kind) {
                ValidationMessage.Kind.Error -> reporting.reportError(text)
                ValidationMessage.Kind.Warning -> reporting.reportWarning(text)
                ValidationMessage.Kind.MandatoryWarning -> if (params.isStrictMode) {
                    reporting.reportError(text)
                } else {
                    reporting.reportWarning(text)
                }
            }
        }
    }

    private fun doValidate(graph: BindingGraph) = params.validationDelegate?.let { delegate ->
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