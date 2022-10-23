package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.lang.BuiltinAnnotation
import com.yandex.daggerlite.lang.common.FieldBase
import javax.inject.Inject

abstract class CtFieldBase : FieldBase() {
    override fun <T : BuiltinAnnotation.OnField> getAnnotation(
        which: BuiltinAnnotation.Target.OnField<T>,
    ): T? {
        val value: BuiltinAnnotation.OnField? = when(which) {
            BuiltinAnnotation.Inject -> (which as BuiltinAnnotation.Inject).takeIf {
                annotations.any { it.hasType<Inject>() }
            }
        }

        @Suppress("UNCHECKED_CAST")
        return which.modelClass.cast(value) as T?
    }
}