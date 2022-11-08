package com.yandex.yatagan.codegen.impl

/**
 * For internal classes provisions
 */
internal fun interface Provider<out T> {
    fun get(): T
}