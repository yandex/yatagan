package com.yandex.dagger3.core

import kotlin.reflect.KProperty

internal class SingleLateInit<T : Any> {
    private var backing: T? = null

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun setValue(thisRef: Any?, prop: KProperty<*>?, value: T) {
        check(backing == null) { "property is already initialized" }
        backing = value
    }

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun getValue(thisRef: Any?, prop: KProperty<*>?): T {
        return checkNotNull(backing) { "property is not yet initialized" }
    }
}

internal fun <T : Any> lateInit() = SingleLateInit<T>()