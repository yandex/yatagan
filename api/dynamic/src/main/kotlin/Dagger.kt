package com.yandex.daggerlite

import com.yandex.daggerlite.dynamic.createBuilderProxy
import com.yandex.daggerlite.dynamic.createComponent
import com.yandex.daggerlite.dynamic.startDaggerRtSession

object Dagger {
    init {
        startDaggerRtSession()
    }

    @JvmStatic
    fun <T : Any> builder(builderClass: Class<T>): T {
        return createBuilderProxy(builderClass)
    }

    @JvmStatic
    fun <T : Any> create(componentClass: Class<T>): T {
        return createComponent(componentClass)
    }
}