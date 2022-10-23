package com.yandex.daggerlite.lang

/**
 * Marker for a lang API that may be used for dagger-lite implementation and is restricted from client (SPI) usage.
 */
@RequiresOptIn(message = "This API is intended to be used only in dagger-lite implementation and " +
        "is not designed for client (SPI) usage.")
annotation class InternalLangApi
