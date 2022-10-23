@file:JvmName("Checks")

package com.yandex.daggerlite.internal

import kotlin.contracts.contract

@PublishedApi
internal fun assertNotNull(instance: Any?, message: String) {
    contract {
        returns() implies (instance != null)
    }
    if (instance == null) {
        throw IllegalStateException(message)
    }
}

@PublishedApi
internal fun <T : Any> checkInputNotNull(input: T?): T {
    assertNotNull(input, "Component input is null or unspecified")
    return input
}

@PublishedApi
internal fun <T : Any> checkProvisionNotNull(instance: T?): T {
    assertNotNull(instance, "Provision result is null")
    return instance
}