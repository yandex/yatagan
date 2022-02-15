package com.yandex.daggerlite.lang.rt

import com.yandex.daggerlite.base.ObjectCache
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import javax.inject.Qualifier
import javax.inject.Scope

internal class RtAnnotationImpl private constructor(
    private val impl: Annotation,
) : AnnotationLangModel {

    override val isScope: Boolean by lazy {
        impl.annotationClass.java.isAnnotationPresent(Scope::class.java)
    }

    override val isQualifier: Boolean by lazy {
        impl.annotationClass.java.isAnnotationPresent(Qualifier::class.java)
    }

    override fun <A : Annotation> hasType(type: Class<A>): Boolean {
        return impl.annotationClass.java == type
    }

    override fun toString() = impl.toString()

    companion object Factory : ObjectCache<Annotation, RtAnnotationImpl>() {
        operator fun invoke(annotation: Annotation) = createCached(annotation, ::RtAnnotationImpl)
    }
}
