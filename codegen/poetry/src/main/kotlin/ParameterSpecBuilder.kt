package com.yandex.daggerlite.codegen.poetry

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeName
import javax.lang.model.element.AnnotationMirror
import kotlin.reflect.KClass

@JavaPoetry
class ParameterSpecBuilder constructor(type: TypeName, name: String) : AnnotatibleBuilder {
    @PublishedApi
    internal val impl: ParameterSpec.Builder = ParameterSpec.builder(type, name)

    inline fun <reified A : Annotation> annotation(
        block: AnnotationSpecBuilder.() -> Unit = {}
    ) {
        impl.addAnnotation(AnnotationSpecBuilder(A::class).apply(block).impl.build())
    }

    override fun annotation(mirror: AnnotationMirror) {
        impl.addAnnotation(AnnotationSpec.get(mirror))
    }

    override fun annotation(
        clazz: KClass<out Annotation>,
        block: AnnotationSpecBuilder.() -> Unit,
    ) {
        impl.addAnnotation(AnnotationSpecBuilder(clazz).apply(block).impl.build())
    }
}