package com.yandex.daggerlite.lang

/**
 * A sealed marker interface for an entity that can be called.
 */
interface CallableLangModel : HasPlatformModel {

    /**
     * Parameters required to call this callable.
     */
    val parameters: Sequence<ParameterLangModel>

    fun <T> accept(visitor: Visitor<T>): T

    interface Visitor<T> {
        fun visitFunction(function: FunctionLangModel): T
        fun visitConstructor(constructor: ConstructorLangModel): T
    }
}