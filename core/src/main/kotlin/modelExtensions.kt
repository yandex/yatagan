package com.yandex.daggerlite.core

import com.yandex.daggerlite.core.lang.AnnotationLangModel
import kotlin.contracts.contract

fun BaseBinding.isScoped(): Boolean {
    contract { returns(true) implies (this@isScoped is ProvisionBinding) }
    return scope() != null
}

fun BaseBinding.scope(): AnnotationLangModel? {
    contract { returnsNotNull() implies (this@scope is ProvisionBinding) }
    return when (this) {
        is ProvisionBinding -> scope
        else -> null
    }
}
