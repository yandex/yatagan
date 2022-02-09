package com.yandex.daggerlite.core.lang

interface HasPlatformModel {
    /**
     * Underlying implementation-specific model, if any.
     */
    val platformModel: Any?
}