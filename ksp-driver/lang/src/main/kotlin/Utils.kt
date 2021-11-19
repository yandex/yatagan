package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.processing.Resolver
import java.io.Closeable

object Utils : Closeable {
    private var resolver_: Resolver? = null
    val resolver: Resolver get() = checkNotNull(resolver_)

    fun init(resolver: Resolver) = this.apply {
        resolver_ = resolver
    }

    override fun close() {
        resolver_ = null
    }
}