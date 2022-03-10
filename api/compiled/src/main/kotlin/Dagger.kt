package com.yandex.daggerlite

object Dagger {
    @JvmStatic
    fun <T : Any> builder(builderClass: Class<T>): T {
        require(builderClass.isAnnotationPresent(Component.Builder::class.java)) {
            "$builderClass is not a builder for a dagger-lite component"
        }
        val componentClass = checkNotNull(builderClass.enclosingClass) {
            "No enclosing component class found for $builderClass"
        }
        require(componentClass.isAnnotationPresent(Component::class.java)) {
            "$builderClass is not a builder for a dagger-lite component"
        }
        val daggerComponentClass = builderClass.classLoader.loadClass(
            "${componentClass.`package`.name}.Dagger$${componentClass.simpleName}")
        return builderClass.cast(daggerComponentClass.getDeclaredMethod("builder").invoke(null))
    }

    @JvmStatic
    fun<T : Any> create(componentClass: Class<T>): T {
        val daggerComponentClass = componentClass.classLoader.loadClass(
            "${componentClass.`package`.name}.Dagger$${componentClass.simpleName}")
        return componentClass.cast(daggerComponentClass.getDeclaredMethod("create").invoke(null))
    }
}