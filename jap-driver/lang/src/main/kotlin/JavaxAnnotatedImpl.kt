package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.base.memoize
import com.yandex.daggerlite.core.lang.AnnotationLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel
import javax.lang.model.AnnotatedConstruct
import javax.lang.model.element.AnnotationMirror
import kotlin.LazyThreadSafetyMode.NONE

internal open class JavaxAnnotatedImpl<T : AnnotatedConstruct>(
    protected val impl: T
) : JavaxAnnotatedLangModel {

    override val annotations: Sequence<CtAnnotationLangModel> by lazy(NONE) {
        impl.annotationMirrors.asSequence().map { JavaxAnnotationImpl(it) }.memoize()
    }

    override fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return getAnnotationIfPresent(type) != null
    }

    override fun <A : Annotation> getAnnotation(type: Class<A>): AnnotationLangModel {
        return JavaxAnnotationImpl(getAnnotationIfPresent(type)!!)
    }

    private fun <A : Annotation> getAnnotationIfPresent(clazz: Class<A>): AnnotationMirror? {
        val annotationClassName = clazz.canonicalName
        for (annotationMirror in impl.annotationMirrors) {
            val annotationTypeElement = annotationMirror.annotationType.asTypeElement()
            if (annotationTypeElement.qualifiedName.contentEquals(annotationClassName)) {
                return annotationMirror
            }
        }
        return null
    }
}