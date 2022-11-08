package com.yandex.yatagan.lang.jap

import com.yandex.yatagan.lang.compiled.CtAnnotated
import com.yandex.yatagan.lang.compiled.CtAnnotationBase
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.Element

internal class JavaxAnnotatedImpl(
    private val impl: Element,
) : CtAnnotated {

    override val annotations: Sequence<CtAnnotationBase> by lazy {
        val annotations = impl.annotationMirrors.map { JavaxAnnotationImpl(it) }
        if (impl.isFromKotlin()) {
            // Means this is from KAPT, and it's known to be reversing annotations order.
            annotations.asReversed()
        } else {
            annotations
        }.asSequence()
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