package com.yandex.yatagan.rt.engine

import com.yandex.yatagan.rt.support.DynamicValidationDelegate
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal inline fun <R> DynamicValidationDelegate.Promise?.awaitOnError(block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    this ?: return block()
    try {
        return block()
    } catch (e: PossiblyInvalidGraphException) {
        // No need to await or wrap anything - already done, as blocks can be nested
        throw e
    } catch (e: Exception) {
        // Await validation, to correctly display the error
        await()
        throw PossiblyInvalidGraphException(e)
    }
}

internal class PossiblyInvalidGraphException(
    cause: Exception,
): RuntimeException("Graph is possibly invalid/had invalid inputs", cause)