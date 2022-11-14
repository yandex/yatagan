package com.yandex.yatagan.lang

public interface HasPlatformModel {
    /**
     * Underlying implementation-specific model, if any.
     *
     * External clients should not rely on this property yielding a specific type.
     */
    public val platformModel: Any?
}