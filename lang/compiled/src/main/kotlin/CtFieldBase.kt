package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.common.FieldBase
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