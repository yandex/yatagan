package com.yandex.daggerlite.lang

/**
 * Models a type constructor.
 */
interface Constructor : Callable, Annotated, Accessible {
    /**
     * An owner and a constructed type of the constructor.
     */
    val constructee: TypeDeclaration

    /**
     * Obtains framework annotation of the given kind.
     *
     * @return the annotation model or `null` if no such annotation is present.
     */
    fun <T : BuiltinAnnotation.OnConstructor> getAnnotation(
        which: BuiltinAnnotation.Target.OnConstructor<T>
    ): T?
}