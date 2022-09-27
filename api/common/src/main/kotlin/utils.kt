@file:[JvmMultifileClass JvmName("Loader")]
package com.yandex.daggerlite.internal

import com.yandex.daggerlite.Component

internal fun loadImplementationClass(componentClass: Class<*>): Class<*> {
    require(componentClass.getAnnotation(Component::class.java)?.isRoot == true) {
        "$componentClass is not a root dagger-lite component"
    }

    // Keep name mangling in sync with codegen!
    val (packageName, binaryName) = splitComponentName(componentClass)
    // no need to parse and join simple names, as codegen joins them with '$' and
    // that's what JVM binary class name already is.
    val implementationName = "$packageName.Dagger\$$binaryName"

    return componentClass.classLoader.loadClass(implementationName)
}

private fun splitComponentName(clazz: Class<*>): Pair<String, String> {
    val name = clazz.name
    return when(val lastDot = name.lastIndexOf('.')) {
        -1 -> "" to name
        else -> name.substring(0, lastDot) to name.substring(lastDot + 1)
    }
}
