package com.yandex.daggerlite.codegen.impl

/**
 * For internal classes provisions
 */
internal fun interface Provider<out T> {
    fun get(): T
}