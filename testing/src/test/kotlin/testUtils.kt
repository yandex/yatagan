package com.yandex.daggerlite.testing

import com.yandex.daggerlite.Dagger
import java.lang.reflect.Method
import kotlin.reflect.KClass

internal operator fun Class<*>.get(name: String, vararg params: KClass<*>): Method {
    return getDeclaredMethod(name, *params.map { it.java }.toTypedArray()).also {
        it.isAccessible = true
    }
}

internal val Any.clz get() = javaClass

inline fun <reified T : Any> Dagger.create(): T = create(T::class.java)

inline fun <reified T : Any> Dagger.builder(): T = builder(T::class.java)