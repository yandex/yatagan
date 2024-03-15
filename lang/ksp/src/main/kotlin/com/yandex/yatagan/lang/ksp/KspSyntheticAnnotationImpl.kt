package com.yandex.yatagan.lang.ksp

import com.yandex.yatagan.lang.Annotation
import com.yandex.yatagan.lang.AnnotationDeclaration

internal class KspSyntheticAnnotationImpl(
    override val annotationClass: AnnotationDeclaration,
    private val values: Map<AnnotationDeclaration.Attribute, Annotation.Value>,
) : KspAnnotationBase() {
    override val platformModel: Any?
        get() = null

    override fun getValue(attribute: AnnotationDeclaration.Attribute): Annotation.Value {
        return requireNotNull(values[attribute]) {
            "Attribute with the name ${attribute.name} is missing"
        }
    }
}