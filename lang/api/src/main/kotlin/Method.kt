package com.yandex.daggerlite.lang

/**
 * Represents a function/method associated with a class from **the Java point of view**.
 * - Constructor is modeled separately by [Constructor].
 * - Top-level kotlin functions are not covered.
 * - Kotlin properties (setters and getters) are also represented by this.
 */
interface Method : Member, Callable, Comparable<Method> {
    /**
     * Whether the function is abstract.
     */
    val isAbstract: Boolean

    /**
     * Return type of the function.
     */
    val returnType: Type

    /**
     * Obtains framework annotation of the given kind.
     *
     * @return the annotation model or `null` if no such annotation is present.
     */
    fun <T : BuiltinAnnotation.OnMethod> getAnnotation(
        which: BuiltinAnnotation.Target.OnMethod<T>
    ): T?

    /**
     * Obtains framework annotations of the given class.
     *
     * @return the list of repeatable annotations or an empty list if no such annotations are present.
     */
    fun <T : BuiltinAnnotation.OnMethodRepeatable> getAnnotations(
        which: BuiltinAnnotation.Target.OnMethodRepeatable<T>
    ): List<T>
}