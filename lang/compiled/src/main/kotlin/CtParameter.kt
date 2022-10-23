package com.yandex.daggerlite.lang.compiled

import com.yandex.daggerlite.Assisted
import com.yandex.daggerlite.BindsInstance
import com.yandex.daggerlite.lang.BuiltinAnnotation
import com.yandex.daggerlite.lang.common.ParameterBase

abstract class CtParameter : ParameterBase() {
    abstract override val annotations: Sequence<CtAnnotation>

    final override fun <T : BuiltinAnnotation.OnParameter> getAnnotation(
        which: BuiltinAnnotation.Target.OnParameter<T>
    ): T? {
        val value: BuiltinAnnotation.OnParameter? = when (which) {
            BuiltinAnnotation.BindsInstance -> BuiltinAnnotation.BindsInstance.takeIf {
                annotations.any { it.hasType<BindsInstance>() }
            }
            BuiltinAnnotation.Assisted ->
                annotations.find { it.hasType<Assisted>() }?.let { CtAssistedAnnotationImpl(it) }
        }
        return which.modelClass.cast(value)
    }
}