package com.yandex.yatagan.lang.compiled

import com.yandex.yatagan.Assisted
import com.yandex.yatagan.BindsInstance
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.common.ParameterBase

abstract class CtParameterBase : ParameterBase() {
    abstract override val annotations: Sequence<CtAnnotationBase>

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