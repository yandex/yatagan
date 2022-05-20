package com.yandex.daggerlite

import com.yandex.daggerlite.Dagger.builder
import com.yandex.daggerlite.Dagger.create
import com.yandex.daggerlite.base.loadServices
import com.yandex.daggerlite.core.impl.ComponentFactoryModel
import com.yandex.daggerlite.core.impl.ComponentModel
import com.yandex.daggerlite.core.lang.InternalLangApi
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.dynamic.RuntimeComponent
import com.yandex.daggerlite.dynamic.RuntimeFactory
import com.yandex.daggerlite.dynamic.awaitOnError
import com.yandex.daggerlite.dynamic.dlLog
import com.yandex.daggerlite.graph.BindingGraph
import com.yandex.daggerlite.graph.impl.BindingGraph
import com.yandex.daggerlite.lang.rt.RtModelFactoryImpl
import com.yandex.daggerlite.lang.rt.TypeDeclarationLangModel
import com.yandex.daggerlite.spi.ValidationPluginProvider
import com.yandex.daggerlite.spi.impl.GraphValidationExtension
import com.yandex.daggerlite.validation.ValidationMessage
import com.yandex.daggerlite.validation.impl.Strings
import com.yandex.daggerlite.validation.impl.validate
import java.lang.reflect.Proxy
import kotlin.system.measureTimeMillis

/**
 * Dagger Lite entry-point object. Create instances of DL components using reflection.
 *
 * Use either [builder] or [create].
 */
object Dagger {
    @Volatile
    private var validationDelegate: DynamicValidationDelegate? = null

    init {
        @OptIn(InternalLangApi::class)
        LangModelFactory.delegate = RtModelFactoryImpl()
    }

    /**
     * Use this to create a component builder instance for root components that declare it.
     *
     * @param builderClass component builder class
     * @return ready component builder instance of the given class
     *
     * @see Component.Builder
     */
    @JvmStatic
    fun <T : Any> builder(builderClass: Class<T>): T {
        val builder: T
        val time = measureTimeMillis {
            require(builderClass.isAnnotationPresent(Component.Builder::class.java)) {
                "$builderClass is not a component builder"
            }
            val graph = BindingGraph(
                root = ComponentFactoryModel(TypeDeclarationLangModel(builderClass)).createdComponent
            )
            val promise = doValidate(graph)
            val factory =  promise.awaitOnError {
                RuntimeFactory(
                    graph = graph,
                    parent = null,
                    validationPromise = promise,
                )
            }
            val proxy = Proxy.newProxyInstance(builderClass.classLoader, arrayOf(builderClass), factory)
            builder = builderClass.cast(proxy)
        }
        dlLog("Dynamic builder creation for `$builderClass` took $time ms")
        return builder
    }

    /**
     * Use this to directly create component instance for components,
     * that do not declare an explicit builder interface.
     *
     * @param componentClass component class
     * @return ready component instance of the given class
     */
    @JvmStatic
    fun <T : Any> create(componentClass: Class<T>): T {
        val componentInstance: T
        val time = measureTimeMillis {
            require(componentClass.isAnnotationPresent(Component::class.java)) {
                "$componentClass is not a component"
            }
            val graph = BindingGraph(ComponentModel(TypeDeclarationLangModel(componentClass)))
            val promise = doValidate(graph)
            val component = promise.awaitOnError {
                RuntimeComponent(
                    graph = graph,
                    parent = null,
                    givenDependencies = emptyMap(),
                    givenInstances = emptyMap(),
                    givenModuleInstances = emptyMap(),
                    validationPromise = promise,
                )
            }
            require(graph.creator == null) {
                "$componentClass has explicit creator interface declared, use `Dagger.builder()` instead"
            }

            val proxy = Proxy.newProxyInstance(componentClass.classLoader, arrayOf(componentClass), component)
            component.thisProxy = proxy
            componentInstance = componentClass.cast(proxy)
        }
        dlLog("Dynamic component creation for `$componentClass` took $time ms")
        return componentInstance
    }

    /**
     * Sets [DynamicValidationDelegate] to be used with all following DL graph creations.
     */
    @JvmStatic
    fun setDynamicValidationDelegate(delegate: DynamicValidationDelegate?) {
        validationDelegate = delegate
    }

    private val pluginProviders by lazy {
        loadServices<ValidationPluginProvider>()
    }

    private fun doValidate(graph: BindingGraph) = validationDelegate?.let { delegate ->
        delegate.dispatchValidation(
            title = graph.toString(),
        ) { reporting: DynamicValidationDelegate.ReportingDelegate ->
            val toValidate = if (delegate.usePlugins) {
                val extension = GraphValidationExtension(
                    validationPluginProviders = pluginProviders,
                    graph = graph,
                )
                listOf(graph, extension)
            } else listOf(graph)

            validate(toValidate).forEach { locatedMessage ->
                val text = Strings.formatMessage(
                    message = locatedMessage.message.contents,
                    color = null,
                    encounterPaths = locatedMessage.encounterPaths,
                    notes = locatedMessage.message.notes,
                )
                when(locatedMessage.message.kind) {
                    ValidationMessage.Kind.Error -> reporting.reportError(text)
                    ValidationMessage.Kind.Warning -> reporting.reportWarning(text)
                }
            }
        }
    }
}