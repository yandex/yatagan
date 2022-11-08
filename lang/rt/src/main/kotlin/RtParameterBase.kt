package com.yandex.yatagan.lang.rt

import com.yandex.yatagan.Assisted
import com.yandex.yatagan.BindsInstance
import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.BuiltinAnnotation
import com.yandex.yatagan.lang.common.ParameterBase

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