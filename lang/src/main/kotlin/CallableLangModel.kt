package com.yandex.daggerlite.core.lang

/**
 * A sealed marker interface for an entity that can be called.
 */
sealed interface CallableLangModel {

    /**
     * Parameters required to call this callable.
     */
    val parameters: Sequence<ParameterLangModel>
}