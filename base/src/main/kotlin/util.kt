package com.yandex.daggerlite.base

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

inline fun <R> ifOrElseNull(condition: Boolean, block: () -> R): R? {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return if (condition) block() else null
}
