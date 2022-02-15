package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.AnnotatedLangModel
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import java.lang.reflect.AnnotatedElement

internal open class RtAnnotatedImpl<T : AnnotatedElement>(
    protected val impl: T,
) : AnnotatedLangModel {
    override val annotations: Sequence<AnnotationLangModel> by lazy {
        impl.declaredAnnotations.asSequence().map { RtAnnotationImpl(it) }.memoize()
    }

    override fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return impl.isAnnotationPresent(type)
    }
}