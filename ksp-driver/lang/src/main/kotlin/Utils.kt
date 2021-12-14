package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getJavaClassByName
import com.google.devtools.ksp.processing.Resolver
import java.io.Closeable
import kotlin.LazyThreadSafetyMode.NONE

object Utils : Closeable {
    private var resolver_: Resolver? = null
    val resolver: Resolver get() = checkNotNull(resolver_)

    val javaLangBoolean by lazy(NONE) {
        resolver.getJavaClassByName("java.lang.Boolean")
    }

    fun init(resolver: Resolver) = this.apply {
        resolver_ = resolver
    }

    override fun close() {
        resolver_ = null
    }
}