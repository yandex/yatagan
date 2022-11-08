package com.yandex.yatagan.codegen.poetry

import javax.lang.model.element.AnnotationMirror
import kotlin.reflect.KClass

interface AnnotatibleBuilder {
    fun annotation(mirror: AnnotationMirror)

    fun annotation(clazz: KClass<out Annotation>, block: AnnotationSpecBuilder.() -> Unit)
}