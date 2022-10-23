package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.lang.AnnotatedLangModel
import com.yandex.daggerlite.lang.AnnotationLangModel

internal class RtAnnotatedImpl(
    private val impl: ReflectAnnotatedElement,
) : AnnotatedLangModel {
    override val annotations: Sequence<AnnotationLangModel> by lazy {
        impl.declaredAnnotations.map { RtAnnotationImpl(it) }.asSequence()
    }

    override fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return impl.isAnnotationPresent(type)
    }
}