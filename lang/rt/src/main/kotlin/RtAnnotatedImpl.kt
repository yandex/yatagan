package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.lang.Annotated
import com.yandex.daggerlite.lang.Annotation

internal class RtAnnotatedImpl(
    private val impl: ReflectAnnotatedElement,
) : Annotated {
    override val annotations: Sequence<Annotation> by lazy {
        impl.declaredAnnotations.map { RtAnnotationImpl(it) }.asSequence()
    }

    override fun <A : kotlin.Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return impl.isAnnotationPresent(type)
    }
}