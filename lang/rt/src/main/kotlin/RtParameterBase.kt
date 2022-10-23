package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.Assisted
import com.yandex.daggerlite.BindsInstance
import com.yandex.daggerlite.lang.Annotation
import com.yandex.daggerlite.lang.BuiltinAnnotation
import com.yandex.daggerlite.lang.common.ParameterBase

abstract class RtParameterBase : ParameterBase() {
    protected abstract val parameterAnnotations: Array<kotlin.Annotation>

    final override val annotations: Sequence<Annotation> by lazy {
        parameterAnnotations.map { RtAnnotationImpl(it) }.asSequence()
    }

    final override fun <T : BuiltinAnnotation.OnParameter> getAnnotation(
        which: BuiltinAnnotation.Target.OnParameter<T>,
    ): T? {
        val annotation: BuiltinAnnotation.OnParameter? = when (which) {
            BuiltinAnnotation.BindsInstance -> BuiltinAnnotation.BindsInstance.takeIf {
                parameterAnnotations.any { it is BindsInstance }
            }

            BuiltinAnnotation.Assisted -> run {
                for (annotation in parameterAnnotations)
                    if (annotation is Assisted)
                        return@run RtAssistedAnnotationImpl(annotation)
                null
            }
        }
        return which.modelClass.cast(annotation)
    }
}