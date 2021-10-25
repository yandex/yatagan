package com.yandex.daggerlite.testing

import java.lang.reflect.Method
import kotlin.reflect.KClass

internal operator fun Class<*>.get(name: String, vararg params: KClass<*>): Method {
    return getDeclaredMethod(name, *params.map { it.java }.toTypedArray()).also {
        it.isAccessible = true
    }
}

internal val Any.clz get() = javaClass