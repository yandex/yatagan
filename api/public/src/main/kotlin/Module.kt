package com.yandex.daggerlite

import kotlin.reflect.KClass

/**
 * Annotates a class/object/interface that contains explicit bindings that contribute to the object graph.
 *
 * If Module declaration contain a *companion object with the default name*, methods from it are also treated like
 * static bindings.
 */
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Module(
    /**
     * Additional [Modules][Module] to be transitively included into a [Component]/another [Module].
     * Allows duplicates, recursively.
     */
    val includes: Array<KClass<*>> = [],

    /**
     * [Component]-annotated interfaces, that should be children in a [Component] which includes this
     * module.
     *
     * Allows duplicates, recursively via [includes].
     *
     * Any included [components][Component] must have [Component.isRoot] to be set to `false`.
     */
    val subcomponents: Array<KClass<*>> = [],
)