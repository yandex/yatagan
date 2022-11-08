@file:[JvmMultifileClass JvmName("Loader")]
package com.yandex.yatagan.common

import com.yandex.yatagan.Component

/**
 * Must be used on root components with declared builder.
 */
@Throws(ClassNotFoundException::class)
fun <T : Any> loadImplementationByBuilderClass(builderClass: Class<T>): T {
    require(builderClass.isAnnotationPresent(Component.Builder::class.java)) {
        "$builderClass is not a builder for a Yatagan component"
    }
    val componentClass = checkNotNull(builderClass.enclosingClass) {
        "No enclosing component class found for $builderClass"
    }
    require(componentClass.getAnnotation(Component::class.java)?.isRoot == true) {
        "$componentClass is not a root Yatagan component"
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
