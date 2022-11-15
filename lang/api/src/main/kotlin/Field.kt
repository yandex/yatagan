package com.yandex.yatagan.lang

/**
 * Models a field from a JVM point of view.
 * Properties are not modeled by this.
 */
public interface Field : Member {

    /**
     * Type of the field.
     */
    public val type: Type

    /**
     * Obtains framework annotation of the given kind.
     *
     * @return the annotation model or `null` if no such annotation is present.
     */
    public fun <T : BuiltinAnnotation.OnField> getAnnotation(
        which: BuiltinAnnotation.Target.OnField<T>
    ): T?
}