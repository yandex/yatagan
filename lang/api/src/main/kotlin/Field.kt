package com.yandex.yatagan.lang

/**
 * Models a field from a JVM point of view.
 * Properties are not modeled by this.
 */
interface Field : Member {

    /**
     * Type of the field.
     */
    val type: Type

    /**
     * Obtains framework annotation of the given kind.
     *
     * @return the annotation model or `null` if no such annotation is present.
     */
    fun <T : BuiltinAnnotation.OnField> getAnnotation(
        which: BuiltinAnnotation.Target.OnField<T>
    ): T?
}