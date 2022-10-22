@file:[JvmMultifileClass JvmName("Loader")]
package com.yandex.daggerlite.common

import com.yandex.daggerlite.Component

/**
 * Must be used on root components with declared builder.
 */
@Throws(ClassNotFoundException::class)
fun <T : Any> loadImplementationByBuilderClass(builderClass: Class<T>): T {
    require(builderClass.isAnnotationPresent(Component.Builder::class.java)) {
        "$builderClass is not a builder for a dagger-lite component"
    }
    val componentClass = checkNotNull(builderClass.enclosingClass) {
        "No enclosing component class found for $builderClass"
    }
    require(componentClass.getAnnotation(Component::class.java)?.isRoot == true) {
        "$componentClass is not a root dagger-lite component"
    }
    val daggerComponentClass = loadImplementationClass(componentClass)
    return builderClass.cast(daggerComponentClass.getDeclaredMethod("builder").invoke(null))
}

/**
 * Must be used on root components with no builder.
 */
@Throws(ClassNotFoundException::class)
fun <T : Any> loadImplementationByComponentClass(componentClass: Class<T>): T {
    val daggerComponentClass = loadImplementationClass(componentClass)
    return componentClass.cast(daggerComponentClass.getDeclaredMethod("create").invoke(null))
}
