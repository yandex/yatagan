package com.yandex.daggerlite.dynamic

import com.yandex.daggerlite.DynamicValidationDelegate
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal inline fun <R> DynamicValidationDelegate.Promise?.awaitOnError(block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    this ?: return block()
    try {
        return block()
    } catch (e: Exception) {
        // Await validation, to correctly display the error
        await()
        throw PossiblyInvalidGraphException(e)
    }
}

internal fun dlLog(message: String) {
    // TODO: provide a dedicated reporting API
    println("[DaggerLiteRt] $message")
}

internal class PossiblyInvalidGraphException(
    cause: Exception,
): RuntimeException("Graph is possibly invalid/had invalid inputs", cause)