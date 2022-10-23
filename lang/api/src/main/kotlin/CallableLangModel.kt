package com.yandex.daggerlite.lang

/**
 * A sealed marker interface for an entity that can be called.
 */
interface CallableLangModel : HasPlatformModel {

    /**
     * Parameters required to call this callable.
     */
    val parameters: Sequence<Parameter>

    fun <T> accept(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visitMethod(method: Method): T
        fun visitConstructor(constructor: Constructor): T
    }
}