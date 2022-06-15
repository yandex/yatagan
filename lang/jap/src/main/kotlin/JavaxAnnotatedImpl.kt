package com.yandex.daggerlite.jap.lang

import com.yandex.daggerlite.generator.lang.CtAnnotatedLangModel
import com.yandex.daggerlite.generator.lang.CtAnnotationLangModel
import javax.lang.model.AnnotatedConstruct
import javax.lang.model.element.AnnotationMirror
import kotlin.LazyThreadSafetyMode.NONE

internal open class JavaxAnnotatedImpl<T : AnnotatedConstruct>(
    protected val impl: T
) : CtAnnotatedLangModel {

    override val annotations: Sequence<CtAnnotationLangModel> by lazy(NONE) {
        impl.annotationMirrors.asSequence().map { JavaxAnnotationImpl(it) }
    }

    override fun <A : Annotation> isAnnotatedWith(type: Class<A>): Boolean {
        return getAnnotationIfPresent(type) != null
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