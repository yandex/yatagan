package com.yandex.daggerlite.core

import kotlin.contracts.contract

fun Binding.isScoped(): Boolean {
    contract { returns(true) implies (this@isScoped is ProvisionBinding) }
    return scope() != null
}

fun Binding.scope(): ProvisionBinding.Scope? {
    contract { returnsNotNull() implies (this@scope is ProvisionBinding) }
    return when (this) {
        is ProvisionBinding -> scope
        else -> null
    }
}
