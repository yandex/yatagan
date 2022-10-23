package com.yandex.daggerlite.lang

/**
 * Models a field from a JVM point of view.
 * Properties are not modeled by this.
 */
interface FieldLangModel : Member {

    /**
     * Type of the field.
     */
    val type: Type
}