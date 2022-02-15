package com.yandex.daggerlite.dynamic

import com.yandex.daggerlite.Component
import com.yandex.daggerlite.core.impl.ComponentFactoryModel
import com.yandex.daggerlite.core.impl.ComponentModel
import com.yandex.daggerlite.core.lang.InternalLangApi
import com.yandex.daggerlite.core.lang.LangModelFactory
import com.yandex.daggerlite.graph.impl.BindingGraph
import com.yandex.daggerlite.lang.rt.RtModelFactoryImpl
import com.yandex.daggerlite.lang.rt.TypeDeclarationLangModel
import java.lang.reflect.Proxy
import kotlin.system.measureTimeMillis

@OptIn(InternalLangApi::class)
fun startDaggerRtSession() {
    LangModelFactory.delegate = RtModelFactoryImpl()
}

// TODO: This is too expensive to use validation in a straightforward way here. Invent a way to do it anyway.

fun <T> createBuilderProxy(builderClass: Class<T>): T {
    val builder: T
    val time = measureTimeMillis {
        require(builderClass.isAnnotationPresent(Component.Builder::class.java)) {
            "$builderClass is not a component builder"
        }
        val factory = RuntimeFactory(
            graph = BindingGraph(
                    root = ComponentFactoryModel(TypeDeclarationLangModel(builderClass)).createdComponent
            ),
            parent = null,
        )
        val proxy = Proxy.newProxyInstance(builderClass.classLoader, arrayOf(builderClass), factory)
        builder = builderClass.cast(proxy)
    }
    println("$TAG: Dynamic builder creation for `$builderClass` took $time ms")
    return builder
}

fun <T> createComponent(componentClass: Class<T>): T {
    val componentInstance: T
    val time = measureTimeMillis {
        require(componentClass.isAnnotationPresent(Component::class.java)) {
            "$componentClass is not a component"
        }

        val graph = BindingGraph(ComponentModel(TypeDeclarationLangModel(componentClass)))
        val component = RuntimeComponent(
            graph = graph,
            parent = null,
            givenDependencies = emptyMap(),
            givenInstances = emptyMap(),
            givenModuleInstances = emptyMap(),
        )
        require(graph.creator == null) {
            "$componentClass has explicit creator interface declared, use `Dagger.builder()` instead"
        }

        val proxy = Proxy.newProxyInstance(componentClass.classLoader, arrayOf(componentClass), component)
        component.thisProxy = proxy
        componentInstance = componentClass.cast(proxy)
    }
    println("$TAG: Dynamic component creation for `$componentClass` took $time ms")
    return componentInstance
}


private const val TAG = "[DaggerLiteRt]"