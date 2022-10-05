package com.yandex.daggerlite.ksp.lang

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getJavaClassByName
import com.google.devtools.ksp.processing.Resolver
import java.io.Closeable

private var utils: ProcessingUtils? = null

internal val Utils: ProcessingUtils get() = checkNotNull(utils) {
    "Not reached: utils are used before set/after cleared"
}

class ProcessingUtils(
    val resolver: Resolver,
) : Closeable {

    val classType by lazy {
        resolver.getClassDeclarationByName("java.lang.Class")!!
    }

    val anyType by lazy {
        resolver.getClassDeclarationByName("kotlin.Any")!!
    }

    val objectType by lazy {
        resolver.getClassDeclarationByName("java.lang.Object")!!
    }

    val kotlinRetentionClass by lazy {
        Utils.resolver.getClassDeclarationByName("kotlin.annotation.Retention")!!
    }

    val javaRetentionClass by lazy {
        Utils.resolver.getJavaClassByName("java.lang.annotation.Retention")!!
    }

    init {
        utils = this
    }

    override fun close() {
        utils = null
    }
}