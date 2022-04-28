package com.yandex.daggerlite

/**
 * Dagger Lite entry-point object. Create instances of DL components by loading generated implementations for
 * the given components/builders classes.
 *
 * Use either [builder] or [create].
 */
object Dagger {

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

    /**
     * Use this to directly create component instance for components,
     * that do not declare an explicit builder interface.
     *
     * @param componentClass component class
     * @return ready component instance of the given class
     */
    @JvmStatic
    fun<T : Any> create(componentClass: Class<T>): T {
        val daggerComponentClass = componentClass.classLoader.loadClass(
            "${componentClass.`package`.name}.Dagger$${componentClass.simpleName}")
        return componentClass.cast(daggerComponentClass.getDeclaredMethod("create").invoke(null))
    }
}