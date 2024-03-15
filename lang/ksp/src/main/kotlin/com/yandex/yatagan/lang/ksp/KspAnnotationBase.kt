package com.yandex.yatagan.lang.ksp

import com.yandex.yatagan.lang.compiled.CtAnnotationBase

internal abstract class KspAnnotationBase : CtAnnotationBase() {
    private val descriptor: String by lazy {
        this@KspAnnotationBase.toString()
    }

    final override fun equals(other: Any?): Boolean {
        return this === other || other is KspAnnotationBase && descriptor == other.descriptor
    }

    final override fun hashCode(): Int {
        return descriptor.hashCode()
    }

    private object Descriptor
}