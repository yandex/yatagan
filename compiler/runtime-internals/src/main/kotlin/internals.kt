package com.yandex.dagger3.compiler.internals

import dagger.Lazy
import java.util.concurrent.atomic.AtomicReferenceArray

private object Uninitialized

class MultiDoubleCheck<T> : Lazy<T> {
    val d = AtomicReferenceArray<Any>(1000)
    override fun get(): T {
        TODO()
    }
}
