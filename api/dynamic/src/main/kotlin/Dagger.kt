package com.yandex.daggerlite

import com.yandex.daggerlite.Dagger.builder
import com.yandex.daggerlite.Dagger.create
import com.yandex.daggerlite.dynamic.createBuilderProxy
import com.yandex.daggerlite.dynamic.createComponent
import com.yandex.daggerlite.dynamic.startDaggerRtSession

/**
 * Dagger Lite entry-point object. Create instances of DL components using reflection.
 *
 * Use either [builder] or [create].
 */
object Dagger {
    init {
        startDaggerRtSession()
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
        return createBuilderProxy(builderClass)
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
        return createComponent(componentClass)
    }
}