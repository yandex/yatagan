package com.yandex.daggerlite.generator.poetry

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import kotlin.reflect.KClass

@JavaPoetry
class AnnotationSpecBuilder(
    clazz: KClass<out Annotation>
) {
    @PublishedApi
    internal val impl: AnnotationSpec.Builder = AnnotationSpec.builder(clazz.java)

    inline fun <reified E : Enum<E>> enumValue(value: E, name: String = "value") {
        impl.addMember(name, "\$T.\$N", E::class.java, value.name)
    }

    fun classValue(type: ClassName, name: String = "value") {
        impl.addMember(name, "\$T.class", type)
    }

    fun classValues(vararg types: ClassName, name: String = "value") {
        value(name) {
            +"{"
            join(types.asIterable()) { type -> +"%T.class".formatCode(type) }
            +"}"
        }
    }

    inline fun value(name: String = "value", block: ExpressionBuilder.() -> Unit) {
        impl.addMember(name, buildExpression(block))
    }

    fun stringValue(value: String, name: String = "value") {
        impl.addMember(name, buildExpression { +"%S".formatCode(value) })
    }

    fun stringValues(vararg values: String, name: String = "value") {
        value(name) {
            +"{"
            join(values.asIterable()) { value -> +"%S".formatCode(value) }
            +"}"
        }
    }

    inline fun <reified A : Annotation> annotation(
        block: AnnotationSpecBuilder.() -> Unit = {}
    ): AnnotationSpec {
        return AnnotationSpecBuilder(A::class).apply(block).impl.build()
    }
}