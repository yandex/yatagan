package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import java.lang.reflect.AnnotatedElement

internal class RtAnnotatedImpl(
    private val impl: AnnotatedElement,
) : AnnotatedLangModel {
    override val annotations: Sequence<AnnotationLangModel> by lazy {
        impl.declaredAnnotations.map { RtAnnotationImpl(it) }.asSequence()
    }

    override fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return impl.isAnnotationPresent(type)
    }
}